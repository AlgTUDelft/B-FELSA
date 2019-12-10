import configparser
from datetime import datetime

class FlexibleLoad:
    def __init__(self, experiment, cfg):
        for key, value in cfg.items():
            if value.lower() in ['true','false']:
                value = (value.lower() == 'true')
            try:
                value = value.lower()
                value = float(value)
                if round(value) == value: value = int(value)
            except: pass
            key = key.replace(' ','_')
            setattr(self, key, value)
    
    @staticmethod
    def load(experiment, file):
        config = configparser.ConfigParser()
        config.read(file)
        loads = [None] * len(config.sections())
        types = { 
            "EV": ElectricVehicle,
            "Battery": Battery
        }
        for i, section in enumerate(config.sections()):
            loads[i] = types[config[section]['type']](experiment, config[section])
        return loads
            
class Battery(FlexibleLoad):
    def __init__(self, experiment, cfg):
        super(Battery, self).__init__(experiment, cfg)
    
class ElectricVehicle(Battery):
    def __init__(self, experiment, cfg):
        super(ElectricVehicle, self).__init__(experiment, cfg)
        fmt = experiment.date_format
        self.arrival = datetime.strptime(self.arrival, fmt)
        self.departure = datetime.strptime(self.departure, fmt)