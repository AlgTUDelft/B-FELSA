import matplotlib
#matplotlib.use("TkAgg")
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg, NavigationToolbar2Tk
from matplotlib.figure import Figure
from matplotlib.widgets import Slider, Button, RadioButtons
import matplotlib.pyplot as plt
import matplotlib.dates as mpd
import tkinter as tk
from tkinter import ttk
from dateutil.parser import parse
import datetime as dt
import numpy as np
import pandas as pd
from plottools import setup_x_axis,make_square

class ExperimentData:
    
    indices = {"shortage": ["Scenario"],
               "overflow": ["Scenario"],
               "cost": ["Scenario"],
               "penalizedcost": ["Scenario"],
               "penalizedcostpermwh": ["Scenario"],
               "costpermwh": ["Scenario"],
               "pda": ["PTU"],
               "pimb": ["PTU"],
            }
    
    def __init__(self, data, name=''):
        self.label = ""
        self.name = name
        self.limits = []
        if isinstance(data, (pd.DataFrame, pd.Series)):
            self.df = data
            return
        file = data
        try:
            with open(file) as f:
                first_line = f.readline()
                second_line = f.readline()
                _split1 = first_line.split(',')
                n_indices = sum([w == '' for w in _split1]) + 2
                indices = [w.split(' ')[0] for w in second_line.split(',')[0:n_indices-1]] + [_split1[n_indices-2]]
                if second_line == '':
                    n_indices = 0
                    indices = []
        except:
            n_indices = 0
            indices = []
        index_col = None if n_indices == 0 else \
            0 if n_indices == 1 else list(range(n_indices-1))
        self.df = pd.read_csv(file, sep=',', header=None, skiprows=(1 if n_indices > 0 else 0), index_col=index_col, error_bad_lines=False)
        self.df.columns = list(range(len(self.df.columns)))
        if len(self.df.columns) >= 1:
            self.df = self.df.stack()
            if n_indices > 0:
                self.df.index.names = indices
            elif self.name in ExperimentData.indices:
                self.df.index.names = [None] + ExperimentData.indices[self.name]
                self.df = self.df.xs(0, level=0)
            else: self.df.index.names = ["I{}".format(i) for i in range(len(self.df.index.names))]
        if len(self.df) == 0: return
        if isinstance(self.df.index, pd.MultiIndex):
            self.df.index = self.df.index.map(\
                lambda w: tuple([int(v.split(" ")[-1]) if isinstance(v, str) else v for v in w]))
        else:
            self.df.index = self.df.index.map(\
                lambda w: int(w.split(" ")[-1]) if isinstance(w, str) else w)
    
    def set_time_index(self, ix):
        try:
            if isinstance(self.df.index, pd.MultiIndex):
                if 'T' in self.df.index.names:
                    i = self.df.index.names.index('T')
                    s = self.df.index.levels[i]
                    self.df.index = self.df.index.set_levels(mpd.date2num(ix[:len(s)]), level='T')
                if 'PTU' in self.df.index.names:
                    i = self.df.index.names.index('PTU')
                    s = self.df.index.levels[i]
                    self.df.index = self.df.index.set_levels(mpd.date2num(ix[:len(s)]), level='PTU')
            else:
                if self.df.index.name in ['T', 'PTU']:
                    name = self.df.index.name
                    self.df.index = mpd.date2num(ix[:len(self.df.index)])
                    self.df.index.name = name
        except: pass
    
    def get_all_choices(self):
        return [i for i in self.df.index.names if not i == 'T' and not i is None] 
        
    def get_default_choices(self):
        return ['L', 'first load id', 'S']
        
    def get_frame(self, master):
        if min(self.df.shape) == 0: return None
        choices = self.get_all_choices()
        frame = tk.Frame(master)
        
        #options = {c: self.df.index.get_level_values(self.indices.index(c)) for c in choices}
        options = {c: sorted(list(set(self.df.index.get_level_values(c)))) for c in choices}
        options = {c: options[c] for c in options if len(options[c]) > 1}
        for c in [v for v in ['PTU', 'T'] if v in options]:
            try:
                options[c] = [mpd.num2date(x) for x in options[c]]
            except: pass
        options = {c: ['all','sum', 'average'] + options[c] if not c in self.get_default_choices() else ['average', 'sum', 'all'] + options[c] for c in options }
        
        #options = {c: options[c] + ['sum', 'average'] for c in options }
        default_options = {c: options[c][0] for c in options}
        fig = plt.figure(figsize=plt.figaspect(0.6))
        canvas = FigureCanvasTkAgg(fig, frame)
        print(default_options)
        self.get_figure(fig=fig, options=default_options)
   
            
        canvas.draw_idle()
        
        
        toolbar = NavigationToolbar2Tk(canvas, frame)
        toolbar.update()

        def change_options(*args):
            canvas.figure.clf()
            try:
                canvas.mpl_disconnect(canvas.figure._onpick_event_id)
            except: pass
            def _parse(c, v):
                if v in ['all', 'sum', 'average']: return v
                if c in ['PTU', 'T']: return mpd.datestr2num(v)
                series = self.df.index.get_level_values(c)
                if series.dtype.kind in 'mMSUO': return v
                if series.dtype.kind in 'ui': return int(v)
                if series.dtype.kind in 'fc': return float(v)
                return v
            options = {c: _parse(c, options_var[c].get()) for c in options_var}
            self.get_figure(fig=canvas.figure, options=options)
            canvas.draw_idle()
            
        options_var = {c: tk.StringVar(frame) for c in options}
        menus = {}
        o_frames = tk.Frame(frame)
        for c in options:
            options_var[c].set(default_options[c])
            o_frame = tk.Frame(o_frames)
            menus[c] = tk.OptionMenu(o_frame, options_var[c], *options[c])
            menus[c].grid(row=0, column=1, sticky=tk.E)
            #menus[c].pack(side=tk.RIGHT, fill=tk.Y, expand=True)
            lbl = tk.Label(o_frame, text=str(c))
            lbl.grid(row=0, column=0, sticky=tk.W)
            #lbl.pack(side=tk.RIGHT, fill=tk.Y, expand=True)
            o_frame.pack(side=tk.RIGHT, fill=tk.Y)
            options_var[c].trace('w', lambda *args: change_options(*args))
        o_frames.pack(side=tk.BOTTOM, fill=tk.BOTH)
        canvas.get_tk_widget().pack(side=tk.TOP, fill=tk.BOTH, expand=True)
        frame.pack(fill=tk.BOTH, expand=True)
        return frame
    
    @staticmethod
    def _get_index_name(index, item, i, _join=", "):
        if isinstance(index, pd.MultiIndex):
            if isinstance(item, tuple):
                name = _join.join(["{} {}".format(index.names[j], item[j]) \
                    if not index.names[j] is None else str(item[j]) \
                    for j in range(len(item))])
            else:
                if name[i] is None or name[i] in ['Run']: name = str(item)
                else: name = "{} {}".format(index.names[i], item)
        else:
            ix_name = index.name
            if ix_name in ['Run']:
                name = str(item)
            else:
                if ix_name is None: ix_name = "Scenario"
                name = "{} {}".format(ix_name, item)
        return name
      
    def get_figure(self, options, series=None, fig=None):
        if min(self.df.shape) == 0: return None
        if fig is None:
            fig = plt.figure(figsize=plt.figaspect(0.6))
        sel_df = self.df
        ExperimentData.remove_single_levels(sel_df)
        for c,v in options.items():
            if v == 'all': continue
            elif v in ['sum', 'average']:
                if isinstance(sel_df.index, pd.MultiIndex):
                    levels = list(range(len(sel_df.index.levels)))
                    levels.remove(sel_df.index.names.index(c))
                    sel_df = sel_df.groupby(level=levels)
                else:
                    sel_df = sel_df.groupby(c)
                if v == 'sum': sel_df = sel_df.sum()
                else: sel_df = sel_df.mean() 
            elif isinstance(sel_df.index, pd.MultiIndex):
                c_ix = sel_df.index.names.index(c)
                sel_df = sel_df.xs(v, level=c_ix)
            else:
                sel_df = sel_df[[v]]
        _ignore_levels = ['PTU', 'run']
        if not 'PTU' in sel_df.index.names: _ignore_levels.append('T')
        unstack_levels = [i for i,v in enumerate(sel_df.index.names) if not v in _ignore_levels]
        while isinstance(sel_df.index, pd.MultiIndex):
            if len(unstack_levels) == 0: break
            sel_df = sel_df.unstack(level=unstack_levels[0])
            unstack_levels = [i for i,v in enumerate(sel_df.index.names) if not v in _ignore_levels]
        if isinstance(sel_df, pd.DataFrame) and len(unstack_levels) > 0:
            sel_df = sel_df.unstack()
        
        if isinstance(sel_df, pd.DataFrame):
            #ExperimentData.drop_duplicate_columns(sel_df)
            ExperimentData.remove_single_levels(sel_df)
        
        #if not series is None:
        if isinstance(fig, plt.Figure): ax = fig.gca()
        else: ax = fig
        if isinstance(sel_df, pd.DataFrame):
            if('T' in sel_df.columns.names):
                values = {}
                if isinstance(sel_df.columns, pd.MultiIndex):
                    i = sel_df.columns.names.index('T')
                    tv = sel_df.columns.levels[i]
                else: tv = sel_df.columns.get_level_values('T')
                plt.subplots_adjust(bottom=0.15)
                t_slider = Slider(plt.axes([0.125, 0.05, 0.775, 0.03], facecolor='lightgoldenrodyellow'),\
                    'Decision Time', 0, len(tv)-1 , 0, valfmt="%d", valstep=1)
                ymax = np.max(sel_df.values)
                ymin = np.min(sel_df.values)
                for v in range(0, len(tv)):
                    values[v] = sel_df.iloc[:, sel_df.columns.get_level_values('T')==tv[v]]
                    if isinstance(values[v].columns, pd.MultiIndex):
                        values[v].columns = values[v].columns.droplevel('T')
                    else:
                        values[v] = values[v][tv[v]]
                sel_df = values[0]
                vline = ax.axvline(x=tv[0], color='k')
                def update(val):
                    val = int(val)
                    vline.set_xdata(tv[val])
                    if isinstance(sel_df, pd.DataFrame):
                        for i, column in enumerate(sel_df):
                            x2, y2 = make_square(None, values[val].iloc[:,i])
                            lines[i].set_ydata(y2)
                    else:
                        x2, y2 = make_square(None, values[val])
                        lines[0].set_ydata(y2)
                    fig.canvas.draw_idle()
                t_slider.on_changed(update)
                fig._t_slider = t_slider
        if isinstance(sel_df, pd.DataFrame):
            lines = []
            lined = dict()
            columns = sel_df.columns
            for i, column in enumerate(sel_df):
                name = ExperimentData._get_index_name(columns, column, i)
                line, = ax.plot(*make_square(sel_df[column]), label=name)
                lines.append(line)
            setup_x_axis(ax, sel_df.index, 'T' in sel_df.index.names or 'PTU' in sel_df.index.names)
            leg = ax.legend()
            for legline, origline in zip(leg.get_lines(), lines):
                legline.set_picker(5)  # 5 pts tolerance
                lined[legline] = origline
                
            def onpick(event):
                # on the pick event, find the orig line corresponding to the
                # legend proxy line, and toggle the visibility
                legline = event.artist
                origline = lined[legline]
                vis = not origline.get_visible()
                origline.set_visible(vis)
                # Change the alpha on the line in the legend so we can see what lines
                # have been toggled
                if vis:
                    legline.set_alpha(1.0)
                else:
                    legline.set_alpha(0.2)
                ax.get_figure().canvas.draw_idle()
                
            ax.get_figure()._onpick_event_id = ax.get_figure().canvas.mpl_connect('pick_event', onpick)
        else:
            if 'PTU' in sel_df.index.names or 'T' in sel_df.index.names:
                line, = ax.plot(*make_square(sel_df))
                lines = [line]
                setup_x_axis(ax, sel_df.index)
            else:
                ax.bar(list(range(len(sel_df))), sel_df)
                ax.set_xticks(list(range(len(sel_df))))
                ax.set_xticklabels([ExperimentData._get_index_name(sel_df.index, row, i, "\n") for i, row in enumerate(sel_df.index)])
        self.setup_labels(sel_df, ax)
        for limit in self.limits:
            limit.draw(ax, options)
        #plt.tight_layout()
        return fig
    
    def setup_labels(self, sel_df, ax):
        if 'T' in sel_df.index.names: ax.set_xlabel("Decision Time")
        elif 'PTU' in sel_df.index.names: ax.set_xlabel("Time")
        ax.set_ylabel(self.label)
        ax.labelsize=16
    
    @staticmethod
    def combine(datas):
        return ExperimentData(pd.concat([d.df for d in datas], keys=list(range(len(datas))), names=['T']))
    
    @staticmethod
    def sum(datas):
        res = datas[0].df
        for data in datas[1:]:
            res = res.add(data.df)
        return ExperimentData(res)
    
    @staticmethod
    def concat(datas, keys, index_name, as_df=False):
        if as_df:
            return pd.concat([d for d in datas], join='inner', keys=keys, names=[index_name])
        else:
            return ExperimentData(pd.concat([d.df for d in datas], join='inner', keys=keys, names=[index_name]))
    
    @staticmethod
    def drop_duplicate_columns(df):        
        duplicate_column_names = {}
        dropped = set()
        for x in range(df.shape[1]):
            if df.columns.values[x] in dropped: continue
            col = df.iloc[:, x]
            duplicate_column_names[df.columns.values[x]] = []
            for y in range(x + 1, df.shape[1]):
                other_col = df.iloc[:, y]
                if col.equals(other_col):
                    duplicate_column_names[df.columns.values[x]].append(df.columns.values[y])
                    dropped.add(df.columns.values[y])
        for key,vals in duplicate_column_names.items():
            df.drop(columns=vals, inplace=True) 
    
    @staticmethod
    def remove_single_levels(sel_df):
        if isinstance(sel_df.index, pd.MultiIndex):
            remove = []
            for i,level in enumerate(sel_df.index.levels):
                if len(level) <= 1: remove.append(i)
            for i in remove[::-1]:
                sel_df.reset_index(level=i, drop=True, inplace=True)
    
    def add_limit(self, value, condition=None, optionname=None,optionvalue=None, *args, **kwargs):
        self.limits.append(Limit(value, condition, optionname, optionvalue, *args, **kwargs))   

class Limit:
    
    def __init__(self, value, condition=None, optionname=None,optionvalue=None, *args, **kwargs):
        self.value = value
        if condition is None:
            if not optionname is None and not optionvalue is None:
                condition = lambda options: options[optionname] == optionvalue
            else: condition = lambda options: True
        self.condition = condition
        self.args = args
        self.kwargs = kwargs
        
    def draw(self, ax, options):
        res = True
        try:
            res = self.condition(options)
        except: pass
        if res:
            ax.axhline(self.value, *self.args, **self.kwargs)