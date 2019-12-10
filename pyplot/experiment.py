from flexible_load import FlexibleLoad, ElectricVehicle
from configparser import ConfigParser
import collections
import pandas as pd
import os
import ast
import numpy as np
import copy
import re
from itertools import product

class Experiment:
    def __init__(self, folder, ini):
        self.folder = folder
        self.ini = ini
        for key, value in ini['config'].items():
            value = Experiment._parse_value_from_ini(key, value)
            key = key.replace(' ','_')
            setattr(self, key, value)
        
        self.pricedatafolder = os.path.join(folder, '..', '..', self.price_data_folder)
        self.output_file = os.path.join(folder,'output.txt')
        self.flexible_loads_file = os.path.join(folder, '..', '..', self.flexible_loads_file)
        if not os.path.isfile(self.flexible_loads_file):
            raise FileNotFoundError("File {} not found".format(self.flexible_loads_file))
        self.date_format = _java_dateformat_to_python_dateformat(self.date_format)
        self.all_loads = FlexibleLoad.load(self, self.flexible_loads_file)
        self._initialize()
    
    def _initialize(self):
        self.loads = self.all_loads[self.first_load_id:self.first_load_id + self.number_of_loads]
        self.online = (self.experiment_type == 'rolling horizon')
        self.req_soc = None
    
    @staticmethod
    def _parse_value_from_ini(key, value):
        if key == 'date format': return value
        if str(value).lower() in ['true','false']:
            value = (str(value).lower() == 'true')
            return value
        try:
            value = str(value).lower()
            value = float(value)
            if round(value) == value: value = int(value)
        except: pass
        return value
    
    def get_increasing_variables(self):
        res = []
        i=1
        while True:
            if not hasattr(self, "increasing{}".format(i)): break
            res.append(getattr(self, "increasing{}".format(i)))
            i += 1
        return res
    
    def get_increasing_variable_list(self, variable):
        i = 1
        while True:
            if not hasattr(self, "increasing{}".format(i)): break
            if getattr(self, "increasing{}".format(i)) == variable:
                if hasattr(self, "list{}".format(i)):
                    return getattr(self, "list{}".format(i)).split(",")
                elif hasattr(self, "start{}".format(i)) and hasattr(self, "end{}".format(i)) and hasattr(self, "step{}".format(i)):
                    low = getattr(self, "start{}".format(i))
                    high = getattr(self, "end{}".format(i))
                    step = getattr(self, "step{}".format(i))
                    return [round(x, 3) for x in np.arange(low, high+step, step)]
                else: break
            i += 1
        return []
    
    def get_run_numbers(self, selection={}):
        lsts = []
        variables = self.get_increasing_variables()
        iterate = [False] * len(variables)
        for i, variable in enumerate(variables):
            if not variable in selection or selection[variable] == 'all':
                lst = self.get_increasing_variable_list(variable)
                iterate[i] = True
            elif selection[variable] == 'average':
                lst = [self.get_increasing_variable_list(variable)]
            else:
                lst = [selection[variable]]
            lsts.append(lst)
        runs = list(product(*lsts))
        res = []
        names = []
        for run in runs:
            name = "_".join([str(b) for i,b in enumerate(run) if iterate[i]]) 
            ru = [b if isinstance(b, list) else [str(b)] for b in run]
            ru = list(product(*ru))
            res.append([self.get_run_number("_".join(r)) for r in ru])
            names.append(name)
        return res, names               
    
    def get_increasing_lists(self):
        i = 1
        res = []
        while True:
            if not hasattr(self, "increasing{}".format(i)): break
            if hasattr(self, "list{}".format(i)):
                lst = [s for s in getattr(self, "list{}".format(i)).split(",")]
            elif hasattr(self, "start{}".format(i)) and hasattr(self, "end{}".format(i)) and hasattr(self, "step{}".format(i)):
                low = getattr(self, "start{}".format(i))
                high = getattr(self, "end{}".format(i))
                step = getattr(self, "step{}".format(i))
                lst = [round(x, 3) for x in np.arange(low, high+step, step)]
            else: break
            res.append(lst)
            i += 1
        return res
    
    def get_run_number(self, foldername):
        if foldername == 'results': return 0
        split = foldername.split("_")
        lsts = self.get_increasing_lists()
        sizes = [len(lst) for lst in lsts] + [1]
        ns = [0]*len(split)
        for i, sub in enumerate(split):
            lst = [str(j) for j in lsts[i]]
            ns[i] = lst.index(sub.lower())
        return sum([sizes[i+1] * ns[i] for i in range(len(split))])
    
    def get_run_by_folder(self, foldername):
        if foldername == 'results': return self
        return self.get_run(self.get_run_number(foldername))
    
    def get_run(self, run):
        dels = ["increasing", "list", "low", "high", "step"]
        experiment = copy.copy(self)
        lsts = self.get_increasing_lists()
        sizes = [len(lst) for lst in lsts] + [1]
        sizes = list(np.cumprod(sizes[::-1])[::-1][1:])
        for i in range(len(lsts)):
            ix = run // sizes[i];
            run -= ix * sizes[i];
            var = getattr(self, "increasing{}".format(i+1)).replace(" ", "_")
            val = lsts[i][ix]
            if var == 'run':
                for key, value in self.ini[val].items():
                    value = Experiment._parse_value_from_ini(key, value)
                    key = key.replace(' ','_')
                    setattr(self, key, value)
            else:
                setattr(experiment, var, Experiment._parse_value_from_ini(var, val))
            for dl in dels:
                if hasattr(experiment, "{}{}".format(dl, i+1)): delattr(experiment, "{}{}".format(dl, i+1))
        experiment._initialize()
        return experiment 
    
    def get_time(self):
        if hasattr(self, "experiment_start") and hasattr(self, "experiment_end"):
            start = self.experiment_start
            end = self.experiment_end
        elif all([isinstance(load, ElectricVehicle) for load in self.loads]):
            start = min([load.arrival for load in self.loads])
            end = max([load.departure for load in self.loads])
        else: raise NotImplementedError("the start and the end of the experiment is unknown")
        return start, end
        
    def get_ptu_series(self):
        start, end = self.get_time()
        ptu_length = self.ptu_length
        return pd.date_range(start, end, freq="{}min".format(ptu_length))
    
    def get_da_conversion_list(self):
        ptus = self.get_ptu_series()
        conv_list = [len(v) for k,v in ptus.groupby(ptus.map(lambda x: x.dayofyear * 24 + x.hour)).items()]
        conv_list[-1] -= 1
        if conv_list[-1] == 0: del conv_list[-1]
        return conv_list
    
    def get_grid_series(self):
        positions = [v.grid_position for v in self.loads]
        df = pd.Series(positions, name='Pos')
        df.index.name ='L'
        return df
        
    def get_grid_plotdata(self, dsoc_df):
        from experiment_plot import ExperimentData 
        if isinstance(dsoc_df.df, pd.Series):
            dsoc_df = dsoc_df.df.to_frame()
        else: dsoc_df = dsoc_df.df
        dsoc_df = dsoc_df * (60.0 / self.ptu_length) / 0.9
        return ExperimentData(dsoc_df.join(self.get_grid_series()).set_index(['Pos'], append=True))         
    
    def get_total_required_soc(self):
        if self.req_soc is None:
            self.req_soc = sum([load.minimum_soc - load.arrival_soc for load in self.loads])
        return self.req_soc         
    
    @staticmethod
    def get_experiment(folder):
        f_ini = folder + "/experiment.ini"
        ini = ConfigParser(dict_type=CaseInsensitiveDict)
        ini.optionxform=str #make config case sensitive
        ini.read(f_ini)
        return Experiment(folder, ini)


def _java_dateformat_to_python_dateformat(fmt):
    mapping = [('yyyy', '%Y'),
               ('yy', '%y'),
               ('MM', '%m'),
               ('M', '%-m'),
               ('dd', '%d'),
               ('d', '%-d'),
               ('HH', '%H'),
               ('H', '%-H'),
               ('mm', '%M'),
               ('m', '%-M'),
               ('ss', '%S'),
               ('s', '%-S'),
               ]
    for k,v in mapping:
        fmt = re.sub('(^|[^%])'+k,'\g<1>'+v, fmt)
    return fmt

#/https://stackoverflow.com/questions/49755480/case-insensitive-sections-in-configparser
class CaseInsensitiveDict(collections.MutableMapping):
    """ Ordered case insensitive mutable mapping class. """
    def __init__(self, *args, **kwargs):
        self._d = collections.OrderedDict(*args, **kwargs)
        self._convert_keys()
    def _convert_keys(self):
        for k in list(self._d.keys()):
            v = self._d.pop(k)
            self._d.__setitem__(k, v)
    def __len__(self):
        return len(self._d)
    def __iter__(self):
        return iter(self._d)
    def __setitem__(self, k, v):
        self._d[k.lower()] = v
    def __getitem__(self, k):
        return self._d[k.lower()]
    def __delitem__(self, k):
        del self._d[k.lower()]
    def copy(self):
        return CaseInsensitiveDict(self._d)