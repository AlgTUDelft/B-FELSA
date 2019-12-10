import matplotlib
#matplotlib.use("TkAgg")
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg, NavigationToolbar2Tk
from matplotlib.figure import Figure
from matplotlib.widgets import Slider, Button, RadioButtons
import matplotlib.pyplot as plt
import matplotlib.dates as mpd
import matplotlib.ticker as mticker
import tkinter as tk
from tkinter import ttk
from dateutil.parser import parse
import datetime as dt
import numpy as np
import pandas as pd
import os
import seaborn
import math
from experiment import Experiment
from experiment_data import ExperimentData
from scipy import stats
from plot_export import PlotExport
from functools import partial
from custom_plot import CustomPlot

class ExperimentPlot:
    progress_bar_popup = None
    
    ignores = ["DClusters", "UClusters", "fd", "fu", "vd", "vu", 'costpermwh', 'penalizedcostpermwh']
    renames = {"pimb": "imbalance",
               "pda": "day ahead",
               "rd": "down",
               "ru": "up",
               "pd_bid": "price bid down",
               "pu_bid": "price bid up",
               "penalizedcost": "penalized costs",
               }
    sums = [{"vars": ['pc', 'pd'], "name": "charge", "label": "Charge (kW)"},
            {"vars": ['rcd', 'rdd'], "name": "reserves down", "label": "Capacity (kW)"},
            {"vars": ['rcu', 'rdu'], "name": "reserves up", "label": "Capacity (kW)"},
            {"vars": ['rcd_bid', 'rdd_bid'], "name": "down bid", "label": "Capacity (kW)"},
            {"vars": ['rcu_bid', 'rdu_bid'], "name": "up bid", "label": "Capacity (kW)"},
           ]
    combis = [{"vars": ['charge', 'down bid', 'up bid'], "name": "charge decisions", "index": 'type', 'label': "Charge (kW)"},
              {"vars": ['down', 'up'], "name": "reserves", "index": "reserves", 'label': "Capacity (kW)"},
              {"vars": ['imbalance', 'day ahead'], "name": "energy purchase", "index": "market", 'label': "Energy (kWh)"},
              {"vars": ['shortage', 'overflow'], 'name': 'violations', 'index': 'violating', 'label': "Percentage of required load (%)"},
              {"vars": ['day ahead price', 'down price', 'up price'], 'name': 'prices', 'index': 'price', 'label': "Price (\u20ac/MWh)"},
              {"vars": ['down price', 'up price'], 'name': 'prices', 'index': 'price', 'label': "Price (\u20ac/MWh)"},
              {"vars": ['cost', 'penalized costs'], 'name': 'cost overview', 'index': 'cost type', 'label': "Operation costs (\u20ac)"} 
            ]
    label = {"price bid down": "Bid price (\u20ac/MWh)",
             "price bid up": "Bid price (\u20ac/MWh)",
             "cost": "Operating costs (\u20ac)",
             "penalizedcost": "Operating costs + penalty (\u20ac)",
             "dsoc": "Change in SOC (kWh)",
             "runtime": "Run Time (s)",
             "soc": "State of Charge at departure (kWh)",
             "grid": "Capcity (kW)",
             "day ahead price": "Day Ahead Price (\u20ac)",
             "down price": "Down Price (\u20ac)",
             "up price": "Up Price (\u20ac)",
             }
    non_zero = lambda exp, data: data.values.max() > 1e-3
    condition = {"price bid down": non_zero,
                 "price bid up": non_zero,
                 "reserves": non_zero,
                 "down": non_zero,
                 "up": non_zero,
                 "rcd": non_zero,
                 "rcu": non_zero,
                 "rdd": non_zero,
                 "rdu": non_zero,
                 "rcd_bid": non_zero,
                 "rcu_bid": non_zero,
                 "rdd_bid": non_zero,
                 "rdu_bid": non_zero,
                 "shortage": non_zero,
                 "overflow": non_zero,
                 "violations": non_zero,
                 "grid": lambda exp, data: exp.number_of_loads > 1}
    div_total_req = lambda exp, v, vs: (v/exp.get_total_required_soc()) * 100
    mwh_to_kwh = lambda exp, v, vs: v*1000
    add_overflow = lambda exp, v, vs: v + (vs['overflow'].df * 200.0 / 100.0 * exp.get_total_required_soc() if 'overflow' in vs else 0)
    transforms = {"violations": [div_total_req],
                  "overflow": [div_total_req],
                  "shortage": [div_total_req],
                  "grid": [mwh_to_kwh],
                  "soc": [mwh_to_kwh],
                  "dsoc": [mwh_to_kwh],
                  "charge": [mwh_to_kwh],
                  "down bid": [mwh_to_kwh],
                  "up bid": [mwh_to_kwh],
                  "charge decisions": [mwh_to_kwh],
                  "down": [mwh_to_kwh],
                  "up": [mwh_to_kwh],
                  "reserves": [mwh_to_kwh],
                  "imbalance": [mwh_to_kwh],
                  "day ahead": [mwh_to_kwh],
                  "energy purchase": [mwh_to_kwh],
                  "penalized costs": [add_overflow]
                  }
    neat = {"Costs": "Operation costs (\u20ac)",
                "Run time": "Run time (s)",
                "Costs per MWh": "Operation costs (\u20ac/MWh)",
                "Penalized costs": "Operation costs + penalty (\u20ac)",
                "Penalized Costs per MWh": "Operation costs + penalty (\u20ac/MWh)",
                "Overflow": "Exceeded battery capacity (%)",
                "Unmet demand": "Unmet demand (%)"
            }
    
    
    @staticmethod
    def get_all_frames(experiment, master):
        frames = []
        frames.extend(ExperimentPlot.get_all_runs_frame(experiment, master))
        frames.extend(ExperimentPlot.get_runs_frame(experiment, master))
        return frames

    @staticmethod
    def get_all_runs_frame(experiment, master):
        folder = experiment.folder 
        dirs = [os.path.join(folder, f) for f in os.listdir(folder) if os.path.isdir(os.path.join(folder, f))]
        if len(dirs) < 1:
            return []
        categories_filename = ["cost", "runtime", "costpermwh", "penalizedcost", "overflow", "shortage"]
        categories = ["Costs", "Run time", "Costs per MWh", "Penalized costs", "Overflow", "Unmet demand"]

        
        variables = {v: ['all', 'average'] + experiment.get_increasing_variable_list(v) for v in experiment.get_increasing_variables()}
        
        ixs = [None] * len(dirs)
        vals = [None] * len(dirs)
        for dir in dirs:
            dfs = {}
            name = os.path.basename(dir)
            number = experiment.get_run_number(name)
            total_soc = experiment.get_run_by_folder(name).get_total_required_soc()
            for cat_filename, cat in zip(categories_filename, categories):
                dfs[cat] = pd.read_csv(os.path.join(dir, "eval/{}.csv".format(cat_filename)), sep=',', header=None).iloc[0]
                if cat in ['Overflow', "Unmet demand"]:
                    dfs[cat] =  dfs[cat] / total_soc * 100
            dfs['Penalized costs'] += 200 * dfs['Overflow'] / 100.0 * total_soc
            ixs[number] = name
            vals[number] = dfs
                        
        def get_frame(master, options, fig_function, ndims=1):
            frame = tk.Frame(master)
            default_options = {c: options[c][i%len(options[c])] for i,c in enumerate(options)}
            fig = plt.figure(figsize=plt.figaspect(0.6))
            canvas = FigureCanvasTkAgg(fig, frame)
            fig.draw_figure = partial(fig_function, experiment=experiment, vals=vals, **default_options)
            fig.draw_figure(fig)  
            canvas.draw_idle()
            toolbar = NavigationToolbar2Tk(canvas, frame)
            toolbar.update()
            fig.savefig = ExperimentPlot.save_figure(fig.savefig.__func__, fig, frame)

            def change_options(*args):
                canvas.figure.clf()
                _options = {c: options_var[c].get() for c in options_var if c in options}
                if ndims == 1:
                    _vars0 = {c: options_var[c].get() for c in options_var if not c in options}
                    canvas.figure.draw_figure = partial(fig_function, experiment=experiment, vals=vals, selectors=[_vars0], **_options)
                elif ndims == 2:
                    _vars0 = {c[2:]: options_var[c].get() for c in options_var if not c in options and c.startswith('H ')}
                    _vars1 = {c[2:]: options_var[c].get() for c in options_var if not c in options and c.startswith('V ')}
                    canvas.figure.draw_figure = partial(fig_function, experiment=experiment, vals=vals, selectors=[_vars0, _vars1], **_options)
                canvas.figure.draw_figure(canvas.figure)
                canvas.draw_idle()
            
            if ndims == 1:
                config = {**options, **variables}
            elif ndims == 2:
                config = {**options}
                for i in ["H", "V"]:
                    for key in variables:
                        config[i + ' ' +key] = variables[key]
            options_var = {c: tk.StringVar(frame) for c in config}
            menus = {}
            o_frames = tk.Frame(frame)
            for c in config:
                if c in options:
                    options_var[c].set(default_options[c])
                else: options_var[c].set(config[c][0])
                o_frame = tk.Frame(o_frames)
                menus[c] = tk.OptionMenu(o_frame, options_var[c], *config[c])
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

                                                               
        def get_scatter_frame(master):
            return get_frame(master, {"Vertical": categories, "Horizontal": categories, "Plot": ['Mean', 'All']}, ExperimentPlot.get_scatter_figure)        
        def get_violinplot_frame(master):
            return get_frame(master, {"Data": categories}, ExperimentPlot.get_violinplot_figure)
        def get_table_frame(master):
            return get_frame(master, {"Data": categories}, ExperimentPlot.get_result_table)
        def get_comparison_frame(master):
            return get_frame(master, {"Data": categories}, ExperimentPlot.get_comparison_table)
        def get_relative_frame(master):
            return get_frame(master, {"Data": categories}, ExperimentPlot.get_relative_figure, ndims=2)
        def get_stochastic_frame(master):
            return get_frame(master, {"Data": categories}, ExperimentPlot.get_stochastic_figure)
        def get_custom_frame(master):
            return get_frame(master, {"Script": ['summary', 'increase']}, ExperimentPlot.get_custom_figure)
               
        frame_violinplot = ExperimentPlot.get_delayed_draw_frame(master,get_violinplot_frame)
        frame_scatter = ExperimentPlot.get_delayed_draw_frame(master,get_scatter_frame)
        frame_table = ExperimentPlot.get_delayed_draw_frame(master,get_table_frame)
        frame_stochastic = ExperimentPlot.get_delayed_draw_frame(master,get_stochastic_frame)
        frame_custom = ExperimentPlot.get_delayed_draw_frame(master,get_custom_frame)
        frames = [(frame_violinplot,'violin'),(frame_scatter, 'scatter'),(frame_table, 'table'),(frame_stochastic, 'stochastic'), (frame_custom, 'custom')]
        if len(ixs) > 1:
            frame_relative = ExperimentPlot.get_delayed_draw_frame(master,get_relative_frame)
            frame_comparison = ExperimentPlot.get_delayed_draw_frame(master,get_comparison_frame)
            frames.extend([(frame_relative, 'relative'), (frame_comparison, 't-tests')])
        return frames
    
    @staticmethod
    def get_data(experiment, vals, name, selectors={}):
        data = [vals[ix][name] for ix in range(len(vals))]
        if len(vals) > 1:
            runs, names = experiment.get_run_numbers(selectors)
        else:
            runs = [[0]]
            names = ['results']
        data = [pd.concat([data[i] for i in run], ignore_index=True) for run in runs]
        return data, names
    
    @staticmethod
    def get_scatter_figure(ax, experiment, vals, Horizontal=None, Vertical=None, Plot=None, selectors=[{}]):
        if isinstance(ax, plt.Figure): ax = ax.gca()
        ax.set_xlabel(ExperimentPlot.neat[Horizontal], fontsize=16)
        ax.set_ylabel(ExperimentPlot.neat[Vertical], fontsize=16)
        ax.labelsize = 16
        ax.set_xscale("log" if Horizontal == 'Run time' else "linear")
        ax.set_yscale("log" if Vertical == 'Run time' else "linear")       
        data1,labels1 = ExperimentPlot.get_data(experiment, vals, Horizontal, selectors[0])
        data2,labels2 = ExperimentPlot.get_data(experiment, vals, Vertical, selectors[0])
        colormap = plt.cm.gist_ncar #nipy_spectral, Set1,Paired
        colorst = [colormap(i) for i in np.linspace(0, 0.9,len(labels1))]
        if ExperimentPlot.paper:
            markers = ["8", "v", "^", "s", "P", "*", 'X', 'd']
        else:  
            markers = ['o']
            ax.grid(True)
        for i,ix in enumerate(labels1):
            if Plot == 'Mean':
                mean1 = data1[i].mean()
                mean2 = data2[i].mean()
                errx = [[mean1 - np.quantile(data1[i], 0.25)], [np.quantile(data1[i], 0.75) - mean1]]  
                erry = [[mean2 - np.quantile(data2[i], 0.25)], [np.quantile(data2[i], 0.75) - mean2]]
                ax.errorbar(mean1, mean2, xerr=errx, yerr=erry, color=colorst[i], label=ix, fmt=markers[i%len(markers)], markersize=9)
            else:
                ax.scatter(data1[i], data2[i], color=colorst[i], label=ix)
        ax.legend() 
    
    @staticmethod        
    def get_violinplot_figure(ax, experiment, vals, Data=None, selectors=[{}]):
        if isinstance(ax, plt.Figure): ax = ax.gca()
        name=Data
        violinplot = True
        ax.set_ylabel(ExperimentPlot.neat[name], fontsize=16)
        ax.labelsize = 16
        data, labels = ExperimentPlot.get_data(experiment, vals, name, selectors[0])
        N = len(labels)
        if not violinplot:
            ax.boxplot(data, vert=True, meanline=True, showmeans=True, widths=[0.95]*N)    
        else:
            ax.violinplot(data, vert=True, showmeans=True, showextrema=False, points=2000, bw_method=1.0/15, widths=[0.95]*N)
        ax.set_yscale("log" if name == 'Run time' else "linear")
        ax.set_xticks(range(1, len(data)+1))
        ax.set_xticklabels(["\n".join(col) if isinstance(col, tuple) else col for col in labels])
        ax.set_xlim(0.45,N+1-0.45)
        
    def get_result_table(ax, experiment, vals, Data=None, selectors=[{}]):
        if isinstance(ax, plt.Figure): ax = ax.gca()
        name = Data
        data, labels = ExperimentPlot.get_data(experiment, vals, name, selectors[0])
        N = len(labels)
        means = [d.mean() for d in data]
        stds = [d.std() for d in data]
        table_str = [[str(means[i])+"\u00b1"+str(stds[i])]  for i in range(len(means))]
        table = ax.table(cellText=table_str, rowLabels=labels, colLabels=["Result"], loc='center')
        table.auto_set_font_size(False)
        table.set_fontsize(11)
        ax.axis('off')
        ax.grid(False)
    
    @staticmethod
    def get_comparison_table(ax, experiment, vals, Data=None, selectors=[{}]):
        if isinstance(ax, plt.Figure): ax = ax.gca()
        name = Data
        data, labels = ExperimentPlot.get_data(experiment, vals, name, selectors[0])
        N = len(labels)
        ps = [[0 if ix1 == ix2 or len(data[ix1]) != len(data[ix2]) else stats.ttest_rel(data[ix1], data[ix2])[1] for ix1 in range(N)] for ix2 in range(N)]
        ps = [[0 if math.isnan(ps[ix1][ix2]) else ps[ix1][ix2] for ix1 in range(N)] for ix2 in range(N)]
        ps_str = [["" if ix1 == ix2 or ps[ix1][ix2] == 0 else "{0:.3f}".format(round(ps[ix1][ix2],3)*100)+"%" for ix1 in range(N)] for ix2 in range(N)]
        colormap = plt.get_cmap('Blues')
        colorst = [colormap(i) for i in np.linspace(0, 0.4, N*N)]            
        cs = [["w" if ix1 == ix2 else colorst[int(ps[ix1][ix2]*N*N)] for ix1 in range(N)] for ix2 in range(N)]
        table = ax.table(cellText=ps_str, cellColours=cs, rowLabels=labels, colLabels=labels, loc='center', colWidths=[1.0/(N+1)]*N)
        table.auto_set_font_size(False)
        table.set_fontsize(11)
        ax.axis('off')
        ax.grid(False)
    
    @staticmethod
    def get_relative_figure(ax, experiment, vals, Data=None, selectors=[{},{}]):   
        if isinstance(ax, plt.Figure): ax = ax.gca()
        ax.labelsize = 16
        ax.set_xscale("log" if Data == 'Run time' else "linear")
        ax.set_yscale("log" if Data == 'Run time' else "linear")       
        data1,labels1 = ExperimentPlot.get_data(experiment, vals, Data, selectors[0])
        data2,labels2 = ExperimentPlot.get_data(experiment, vals, Data, selectors[1])
        colormap = plt.cm.gist_ncar #nipy_spectral, Set1,Paired  
        labels = ["-".join([v for v in selectors[i].values() if not v in ['all', 'average']]) for i in range(2)]
        labels = [label if len(label) > 0 else "all" for label in labels ]
        data = [pd.concat([d for d in data1]), pd.concat([d for d in data2])]
        ax.set_xlabel(labels[0], fontsize=16)
        ax.set_ylabel(labels[1], fontsize=16)
        if len(data[0]) != len(data[1]): return            
        ax.scatter(data[0], data[1], label=ExperimentPlot.neat[Data])
        lims = [ np.min([ax.get_xlim(), ax.get_ylim()]),
                np.max([ax.get_xlim(), ax.get_ylim()]) ]
        ax.plot(lims, lims, 'k--', alpha=0.75, zorder=0)
        ax.legend()
        ax.grid(True)
    
    @staticmethod
    def get_stochastic_figure(ax, experiment, vals, Data=None, selectors=[{}]):
        if isinstance(ax, plt.Figure): ax = ax.gca()
        name=Data
        violinplot = True
        ax.set_xlabel("Cumulative probability (%)", fontsize=16)
        ax.set_ylabel(ExperimentPlot.neat[name], fontsize=16)
        ax.labelsize = 16
        data, labels = ExperimentPlot.get_data(experiment, vals, name, selectors[0])
        labels = [label.upper() for label in labels]
        N = len(labels)
        line_styles = ['-', '-', ':', ':', '-.','-.', '--', '--']
        markers = ["8", "v", "^", "s", "P", "*", 'X', 'd']
        color = ['k', 'dimgrey', 'royalblue', 'maroon', 'darkgoldenrod', 'forestgreen', 'indigo', 'mediumturquoise']
        #color = ['k', 'dimgrey']
        for ix in range(N):
            data_sorted = np.sort(data[ix])
            p = 100. * np.arange(len(data[ix])) / (len(data[ix]) - 1)
            if ExperimentPlot.paper:
                if name in ['Unmet demand', 'Overflow']:
                    selected = np.round(data_sorted*4, 2) > 0
                    data_sorted = data_sorted[selected]
                    if len(data_sorted) < 2: continue
                    p = 100.0 - p[selected]
                mark_every = 0.2#max(1,len(data[ix]) // 5)
                step_size = ([1] + [d for d in [1,2,3,5,7,11,13,17] if d < (N+1) // 2 and N % d > 0])[-1]
                mark_every = ((mark_every // N) * ((ix * step_size) % N), mark_every)
                ax.plot(p, data_sorted, marker=markers[ix%len(markers)], markeredgewidth=1.0, linestyle=line_styles[ix%len(line_styles)],\
                        color=color[ix%len(color)], markersize=4,
                        label=("\n".join(labels[ix]) if isinstance(labels[ix], tuple) else labels[ix]), markevery=mark_every)                
            else:
                ax.plot(p, data_sorted, label=("\n".join(labels[ix]) if isinstance(labels[ix], tuple) else labels[ix]))
        if name in ['Run time']:
            ax.set_yscale("log")
        if ExperimentPlot.paper and name in ['Unmet demand', 'Overflow']:
            ax.set_ylim(0.0,15)
            ax.set_xscale("log")
            ax.xaxis.set_major_formatter(mticker.ScalarFormatter())
            ax.xaxis.get_major_formatter().set_scientific(False)
            ax.set_xlim(95, max(0, ax.get_xlim()[0]))
            labels = ax.get_xticks().tolist()
            labels = [str(100-float(x)) if x else x for x in labels]
            ax.set_xticklabels(labels)
        elif ExperimentPlot.paper and name == 'Penalized costs':
            ax.set_ylim(0.2,0.6)
        ax.legend()           
    
    @staticmethod
    def get_custom_figure(fig, experiment, vals, Script=None, selectors=[{}]): 
        getattr(CustomPlot, Script)(fig, experiment, vals, selectors)
               
    @staticmethod      
    def get_figure_frames(experiment, master):
        if not isinstance(experiment, Experiment):
            experiment = Experiment.get_experiment(experiment)
        frames = []
        if experiment.verbose > 1:
            frames.extend(ExperimentPlot.get_online_detailed(master, experiment))
        return frames
    
    @staticmethod
    def get_experiment_data(experiment, folder, columns=None, remove=True):
        datas = {}
        if experiment.online:
            dirs = [f for f in os.listdir(folder) if os.path.isdir(os.path.join(folder, f)) and not f in ['eval', 'data']]
            dirs.sort(key=lambda f: int(f.replace("PTU ", "")))
            dirs = [os.path.join(folder, f) for f in dirs]
            if len(dirs) == 0: return {}
            ExperimentPlot.create_progress_bar()
            files = [f for f in os.listdir(dirs[0]) if os.path.isfile(os.path.join(dirs[0], f)) and f.endswith(".csv")]
            files.extend([os.path.join("eval", f) for f in os.listdir(os.path.join(dirs[0], "eval")) if os.path.isfile(os.path.join(folder, "eval", f)) and f.endswith(".csv")])
            for i, f in enumerate(files):
                name = os.path.splitext(os.path.basename(f))[0]
                if name in ExperimentPlot.ignores or (not columns is None and not name in columns): continue
                data_list = [ExperimentData(os.path.join(dir, f), name) for dir in dirs]
                if False:#all([data_list[i].df.equals(data_list[i+1].df) for i in range(len(data_list)-1)]):
                    datas[name] = data_list[0]
                else: datas[name] = ExperimentData.combine(data_list)
                if name == 'pda': datas[name] = ExperimentPlot.transform_da_to_ptu(experiment, datas[name])
                ExperimentPlot.update_progress_bar(current=i+1, total=len(files))
            ExperimentPlot.destroy_progress_bar()
        files = [os.path.join(folder, f) for f in os.listdir(folder) if os.path.isfile(os.path.join(folder, f)) and f.endswith(".csv")]
        if not experiment.online:
            folder_eval = os.path.join(folder, "eval")
            files.extend([os.path.join(folder_eval, f) for f in os.listdir(folder_eval) if os.path.isfile(os.path.join(folder_eval, f)) and f.endswith(".csv")])
        ExperimentPlot.create_progress_bar()
        for i, file in enumerate(files):
            name = os.path.splitext(os.path.basename(file))[0]
            if name in ExperimentPlot.ignores or (not columns is None and not name in columns): continue
            datas[name] = ExperimentData(file, name)
            if name == 'pda': datas[name] = ExperimentPlot.transform_da_to_ptu(experiment, datas[name])
            ExperimentPlot.update_progress_bar(current=i+1, total=len(files))
        ExperimentPlot.destroy_progress_bar()
        if 'dsoc' in datas:
            datas['grid'] = experiment.get_grid_plotdata(datas['dsoc'])
        pricedatas = ExperimentPlot.get_pricedata(experiment, folder)
        for d in pricedatas:
            datas[d.name] = d
        for org, newval in ExperimentPlot.renames.items():
            if org in datas:
                datas[newval] = datas[org]
                if remove: del datas[org]
        for name in ExperimentPlot.label:
            if not name in datas: continue
            datas[name].label = ExperimentPlot.label[name]
        for s in ExperimentPlot.sums:
            if all(v in datas for v in s['vars']):
                datas[s['name']] = ExperimentData.sum([datas[v] for v in s['vars']])
                datas[s['name']].label = s['label']
                if remove: 
                    for v in s['vars']: del datas[v]
        for s in ExperimentPlot.combis:
            if all(v in datas for v in s['vars']):
                try:
                    datas[s['name']] = ExperimentData.concat([datas[v] for v in s['vars']], s['vars'], s['index'])
                    datas[s['name']].label = s['label']
                    if remove:
                        for v in s['vars']: del datas[v]
                except:
                    print("error in combining {}".format(", ".join(s['vars'])))
        if remove:
            for name in ExperimentPlot.condition:
                if not name in datas: continue
                if not ExperimentPlot.condition[name](experiment, datas[name].df):
                    del datas[name]
        for name,funcs in ExperimentPlot.transforms.items():
            if not name in datas: continue
            for func in funcs:
                datas[name].df = func(experiment, datas[name].df, datas) 
        for name, data in datas.items():
            data.set_time_index(experiment.get_ptu_series())
            
        if 'soc' in datas:
            for i,load in enumerate(experiment.loads):
                datas['soc'].add_limit(load.battery_capacity * 1000, optionname="L", optionvalue=i, color='r', linestyle=':')
                datas['soc'].add_limit(load.minimum_soc * 1000, optionname="L", optionvalue=i, color='b', linestyle=':')
            
        
        return datas
    
    @staticmethod
    def transform_da_to_ptu(experiment, data):
        conv_list = experiment.get_da_conversion_list()
        indices = data.df.index.names
        if len(indices) > 1:
            hours = sum([[data.df.xs(i, level='PTU')] * conv_list[i] for i in range(len(conv_list))], [])
            res = pd.concat(hours, join='inner', keys = list(range(sum(conv_list))), names=['PTU'])
            res.index = res.index.reorder_levels(indices)
        else:
            hours = sum([[data.df.iloc[i]] * conv_list[i] for i in range(len(conv_list))], [])
            res = pd.Series(hours)
            res.index.names = indices
        
        return ExperimentData(res)
    
    @staticmethod
    def get_decisions_frame(experiment, master, folder):
        datas = ExperimentPlot.get_experiment_data(experiment, folder)
        frames = []
        for name, data in datas.items():
            _frame = ExperimentPlot.get_delayed_draw_frame(master, data.get_frame)
            frames.append((_frame, name))
        return frames
    
    @staticmethod
    def get_frame_for_figure(master, figure_function):
        frame = tk.Frame(master)
        fig = plt.figure(figsize=plt.figaspect(0.6))
        canvas = FigureCanvasTkAgg(fig, frame)
        figure_function(fig=fig)
        canvas.draw_idle()
        canvas.get_tk_widget().pack(side=tk.TOP, fill=tk.BOTH, expand=True)
        toolbar = NavigationToolbar2Tk(canvas, frame)
        toolbar.update()
        fig.savefig = ExperimentPlot.save_figure(fig.savefig.__func__, fig, frame)
        canvas.get_tk_widget().pack(side=tk.TOP, fill=tk.BOTH, expand=True)
        frame.pack(fill=tk.BOTH, expand=True)
        return frame
    
    @staticmethod
    def get_delayed_draw_frame(frame, frame_function=None, tab_control=None, fig_function=None):
        _frame = tk.Frame(frame)
        _frame._opened_before = False
        if frame_function is None:
            frame_function = lambda o: ExperimentPlot.get_frame_for_figure(o, fig_function)
        def add_frame(e, o=_frame):
            if not o._opened_before:
                fig = frame_function(o)
                o.unbind('<Visibility>')
                o._opened_before = True
                if fig is None and not tab_control is None:
                    tab_control.forget(o)
                elif not fig is None:
                    fig.pack(fill=tk.BOTH, expand=True)
        _frame.bind('<Visibility>', add_frame)
        return _frame
        
    @staticmethod
    def get_run_frames(experiment, master, folder):
        return ExperimentPlot.get_decisions_frame(experiment, master, folder)      
     
    @staticmethod
    def create_progress_bar():
        if ExperimentPlot.progress_bar_popup is None:
            ExperimentPlot.progress_bar_popup = tk.Toplevel()
            popup = ExperimentPlot.progress_bar_popup
            tk.Label(popup, text="Reading data folder. Please wait...").pack(fill=tk.X, expand=1, side=tk.TOP)
            popup.progress_vars = []
            popup.create_progress_bars = []
            popup.level = 0
        else: popup = ExperimentPlot.progress_bar_popup
        
        if popup.level < len(popup.progress_vars):
            popup.progress_vars[popup.level].set(0)
        else:
            popup.progress_vars.append(tk.DoubleVar())
            popup.create_progress_bars.append(ttk.Progressbar(popup, variable=popup.progress_vars[popup.level], maximum=100))
            popup.create_progress_bars[popup.level].pack(fill=tk.X, expand=1, side=tk.TOP)
        popup.level += 1
        popup.pack_slaves()
        popup.update()
            
    @staticmethod
    def destroy_progress_bar():
        if not ExperimentPlot.progress_bar_popup is None:
            popup = ExperimentPlot.progress_bar_popup
            if popup.level == 1:
                popup.destroy()
                ExperimentPlot.progress_bar_popup = None
            else:
                popup.level -= 1
                popup.progress_vars[popup.level].set(0)
                popup.update()
                
    @staticmethod
    def update_progress_bar(current=0, total=100):
        popup = ExperimentPlot.progress_bar_popup
        popup.progress_vars[popup.level-1].set(current * 100 / total)
        popup.update()
    
    @staticmethod
    def get_runs_frame(experiment, master):
        folder = experiment.folder 
        dirs = [os.path.join(folder, f) for f in os.listdir(folder) if os.path.isdir(os.path.join(folder, f))]
        if len(dirs) == 0:
            return None
        if len(dirs) == 1:
            name = os.path.basename(dirs[0])
            return ExperimentPlot.get_run_frames(experiment.get_run_by_folder(name), master, dirs[0])
        frames = []
        for i, dir in enumerate(dirs):
            name = os.path.basename(dir)
            def _get_run_frames(master, dir=dir, name=name):
                frame = tk.Frame(master)
                tab_control = ttk.Notebook(frame, width=200, height=100)
                tab_control.pack(fill=tk.BOTH, expand=True)
                dir_frames = ExperimentPlot.get_run_frames(experiment.get_run_by_folder(name), tab_control, dir)
                if len(dir_frames) > 1:
                    for _frame, _name in dir_frames:
                        tab_control.add(_frame, text=_name)
                return frame
            frame = ExperimentPlot.get_delayed_draw_frame(master, _get_run_frames, master)
            frames.append((frame, name))
        return frames
    
    @staticmethod
    def get_pricedata(experiment, folder):
        day_ahead = experiment.day_ahead
        per_minute = experiment.per_minute_evaluation
        pricedatafolder = experiment.pricedatafolder
        outputdatafolder = os.path.join(folder, "data")
        if not os.path.isdir(outputdatafolder):
            outputdatafolder = pricedatafolder
        ptu_size = experiment.ptu_length
        
        x = mpd.date2num(list(experiment.get_ptu_series()))
        exp_datas = []
        
        if per_minute:
            dfmin = pd.read_csv(os.path.join(pricedatafolder, 'minute.csv'), header=0, index_col=0,
                                parse_dates=[[0, 2]], dayfirst=True)
            xmin15 = dfmin.index[np.arange(0, len(dfmin), ptu_size)]
            xmin = mpd.date2num(list(dfmin.index))
            xmin15 = mpd.date2num(list(xmin15))
            xsel = np.bitwise_and(xmin >= x[0], xmin <= x[-1])
            xsel15 = np.bitwise_and(xmin15 >= x[0], xmin15 <= x[-1])
            dfmin = dfmin[xsel]
            xmin = xmin[xsel]
            xmin15 = xmin15[xsel15]
            mid = dfmin['Mid_prijs_opregelen']
            up = dfmin['Hoogste_prijs_opregelen']
            down = dfmin['Laagste_prijs_afregelen']
            imb = np.fmax(up, down)
            imb = imb.fillna(mid)
    
            mid15 = dfmin['Mid_prijs_opregelen'].groupby(np.arange(len(dfmin))//ptu_size).mean()
            up15 = dfmin['Hoogste_prijs_opregelen'].groupby(np.arange(len(dfmin))//ptu_size).max()
            down15 = dfmin['Laagste_prijs_afregelen'].groupby(np.arange(len(dfmin))//ptu_size).min()
            imb15 = np.fmax(up15, down15)
            imb15 = imb15.fillna(mid15)
        else:
            down_data = pd.read_csv(os.path.join(outputdatafolder, 'price_down.csv'), header=0, index_col=0, parse_dates=[0], dayfirst=False)
            up_data = pd.read_csv(os.path.join(outputdatafolder, 'price_up.csv'), header=0, index_col=0, parse_dates=[0], dayfirst=False)
            try:
                down_data.index = mpd.date2num(list(down_data.index))
                up_data.index = mpd.date2num(list(up_data.index))
                xsel_down = np.bitwise_and(down_data.index >= x[0], down_data.index <= x[-1])
                down_data = down_data[xsel_down]
                xsel_up = np.bitwise_and(up_data.index >= x[0], up_data.index <= x[-1])
                up_data = up_data[xsel_up]
                if isinstance(down_data, pd.DataFrame): down_data.columns = list(range(len(down_data.columns)))
                if isinstance(up_data, pd.DataFrame): up_data.columns = list(range(len(up_data.columns)))
                if isinstance(down_data, pd.DataFrame) and len(down_data.columns) > 1:
                    if experiment.number_of_evaluation_scenarios > 1:
                        down_data = down_data.iloc[:, experiment.start_evaluation_scenario:experiment.start_evaluation_scenario+experiment.number_of_evaluation_scenarios]
                    else: down_data = down_data[down_data.columns[experiment.start_evaluation_scenario]]
                if isinstance(up_data, pd.DataFrame) and len(up_data.columns) > 1:
                    if experiment.number_of_evaluation_scenarios > 1:
                        up_data = up_data.iloc[:, experiment.start_evaluation_scenario:experiment.start_evaluation_scenario+experiment.number_of_evaluation_scenarios]
                    else: up_data = up_data[up_data.columns[experiment.start_evaluation_scenario]]
            except: pass
            if isinstance(down_data, pd.DataFrame) and len(down_data.columns) > 1:
                down_data = down_data.stack()
                down_data.index = down_data.index.map(\
                    lambda w: tuple([int(v.replace("S","").replace("PTU ","")) if isinstance(v, str) else v for v in w]))
                down_data.index.names = ["PTU", "Scenario"]
            else: down_data.index.name = "PTU"
            if isinstance(up_data, pd.DataFrame) and len(up_data.columns) > 1:
                up_data = up_data.stack()
                up_data.index = up_data.index.map(\
                    lambda w: tuple([int(v.replace("S","").replace("PTU ","")) if isinstance(v, str) else v for v in w]))
                up_data.index.names = ["PTU", "Scenario"]
            else: up_data.index.name = "PTU"
            exp_datas.extend([ExperimentData(down_data, "down price"), ExperimentData(up_data, "up price")])
        
        if day_ahead:
            dfda = pd.read_csv(os.path.join(pricedatafolder, 'da.csv'), header=0, index_col=0,
                               parse_dates=[[0, 1]], dayfirst=True)
            dfda.index = mpd.date2num(list(dfda.index))
            xsel = np.bitwise_and(dfda.index >= x[0], dfda.index <= mpd.date2num(mpd.num2date(x[-1]) + dt.timedelta(hours=1)))
            dfda = dfda[xsel]
            da = dfda.iloc[:,0]
            conv_list = experiment.get_da_conversion_list()
            hours = sum([[da.iloc[i]] * conv_list[i] for i in range(len(conv_list))], [])
            res = pd.Series(hours)
            res.index.name = "PTU"
            if experiment.number_of_evaluation_scenarios > 1:
                res = pd.concat([res] * experiment.number_of_evaluation_scenarios, keys=list(range(experiment.number_of_evaluation_scenarios)), names=['Scenario'])
                res.index = res.index.reorder_levels(['PTU', 'Scenario'])
            exp_data = ExperimentData(res, "day ahead price")
            exp_data.set_time_index(experiment.get_ptu_series())
            exp_datas.append(exp_data)
            
        return exp_datas

    @staticmethod
    def save_figure(save_figure, figure, master):
        def func(*args):
            figure.savefig = save_figure.__get__(figure, figure.__class__)
            export_frame = PlotExport(master, figure, save_figure, *args)
            return 
        return func
