import matplotlib.pyplot as plt
import os
import matplotlib.dates as mpd
import itertools
import pandas as pd
import numpy as np
from plottools import setup_x_axis,make_square

class CustomPlot:
    
    @staticmethod
    def summary(fig, experiment, vals, selectors):
        fig, (ax1, ax2, ax3, lax) = plt.subplots(1, 4, gridspec_kw={"width_ratios":[4,4,4,1]}, num=fig.number)
        from experiment_plot import ExperimentPlot
        ExperimentPlot.get_stochastic_figure(ax1, experiment, vals, 'Penalized costs', selectors)
        ExperimentPlot.get_stochastic_figure(ax2, experiment, vals, 'Unmet demand', selectors)
        ExperimentPlot.get_stochastic_figure(ax3, experiment, vals, 'Overflow', selectors)
        ax1.set_title(ax1.get_ylabel())
        ax2.set_title(ax2.get_ylabel())
        ax3.set_title(ax3.get_ylabel())
        ax1.set_ylabel("")
        ax2.set_ylabel("")
        ax3.set_ylabel("")
        ax1.get_legend().remove()
        ax2.get_legend().remove()
        ax3.get_legend().remove()
        
        h,l = ax1.get_legend_handles_labels()
        lax.legend(h,l, borderaxespad=0)
        lax.set_axis_off()
                   
    @staticmethod
    def increase(fig, experiment, vals, selectors):
        from experiment_plot import ExperimentPlot
        from experiment_data import ExperimentData
        dirs = [os.path.join(experiment.folder, f) for f in os.listdir(experiment.folder) if os.path.isdir(os.path.join(experiment.folder, f))]
        datas = {os.path.basename(dir): ExperimentPlot.get_experiment_data(experiment, dir, ["penalizedcost", "overflow", "shortage"], False) for dir in dirs}
        data_costs = ExperimentData.concat([d['penalized costs'] for d in datas.values()], datas.keys(), "Run")
        data_shortage = ExperimentData.concat([d['shortage'] for d in datas.values()], datas.keys(), "Run")
        data_overflow = ExperimentData.concat([d['overflow'] for d in datas.values()], datas.keys(), "Run")

        inc_lists = experiment.get_increasing_lists()
        if len(inc_lists) == 1: len_q = 1
        else: len_q = np.cumprod([len(l) for l in inc_lists[1:]])[0]
        fig, axes = plt.subplots(len_q+1, 3, gridspec_kw={"width_ratios":[4,4,4], "height_ratios":[1]+[6]*len_q}, num=fig.number, sharex=True)
        
        for dix, data in enumerate([data_costs, data_shortage, data_overflow]):
            data = data.df
            if 'Scenario' in data.index.names:
                levels = list(range(len(data.index.levels)))
                levels.remove(data.index.names.index('Scenario'))
                data = data.groupby(level=levels).mean()
            ptu_level_index = data.index.names.index('T')
            run_level_index = data.index.names.index('Run')
            run_index = data.index.levels[run_level_index].to_series().str.split("_").map(lambda w: tuple(w))
            index = list(itertools.product(unique(list(zip(*run_index.values))[0]), unique(list(zip(*run_index.values))[1]), data.index.levels[ptu_level_index]))
            data.index = pd.MultiIndex.from_tuples(index, names=['Method' , 'Quality', "T"])
            
            ptu_level_index = data.index.names.index('T')
            method_level_index = data.index.names.index('Method')
            quality_level_index = data.index.names.index('Quality')
            methods = data.index.levels[method_level_index]
            try:
                method_order_key = {"DI": 0, "OP": 1, "MR": 2, "QO":3, "DT": 4, "SO1": 5, "SO2": 6, "PI": 7}
                methods = np.array(sorted(methods, key=method_order_key.get))
            except: pass
            
            g = data.groupby(['Method', 'Quality', data.index.get_level_values('T').map(mpd.num2date).hour], sort=False)
            key = {mpd.num2date(x).hour: x for x in data.index.get_level_values('T')}
            data = g.mean()
            min_y, max_y = float('inf'),-float('inf')
            
            line_styles = ['-', '-', ':', ':', '-.','-.', '--', '--']
            markers = ["8", "v", "^", "s", "P", "*", 'X', 'd']
            color = ['k', 'dimgrey', 'royalblue', 'maroon', 'darkgoldenrod', 'forestgreen', 'indigo', 'mediumturquoise']
            
            for i, ax in enumerate(axes[:,dix]):
                if i==0: continue
                if i==1: ax.set_title(['Operation costs + penalty (\u20ac)', 'Unmet demand (%)', 'Exceeded battery capacity (%)'][dix])
                q = data.index.levels[quality_level_index][i-1]
                if dix==0: ax.set_ylabel('q = '+str(q))
                lines = []
                N = len(methods)
                for mx, m in enumerate(methods):
                    mx2 = mx+1
                    if mx2 >= 2: mx2 += 1
                    d = data[m,q]
                    d.index = d.index.map(lambda w: key[w])
                    #d.index = pd.MultiIndex.from_tuples(g.idxmin().values)
                    #d = d[m, q]
                    if ExperimentPlot.paper:
                        mark_every = max(1,len(d) // 5)
                        step_size = ([1]+[e for e in [1,2,3,5,7,11,13,17] if e < (N+1) // 2 and N % e > 0])[-1]
                        mark_every = (max(mark_every // N,1) * ((mx * step_size) % N), mark_every)
                        print("{}: {}, {}, {}".format(mx, step_size, mark_every, N))
                        line, = ax.plot(d, label=m, marker=markers[mx2%len(markers)], markeredgewidth=1.0, linestyle=line_styles[mx2%len(line_styles)], \
                                    color=color[mx2%len(color)], markersize=4, markevery=mark_every)
                    else:
                        line, = ax.plot(d, label=m)
                    lines.append(line)
                    min_y = min(min_y, ax.get_ylim()[0])
                    max_y = max(max_y, ax.get_ylim()[1])
                setup_x_axis(ax, d.index, div=4)
                if i < len_q:
                    ax.set_xticklabels([])
            for ax in axes[:,dix]:
                ax.set_ylim(min_y, max_y)
        gs = axes[0, 0].get_gridspec()
        for ax in axes[0, :]:
            ax.remove()
        l_ax = fig.add_subplot(gs[0, :])
        l_ax.legend(lines, methods, loc='upper center', ncol=len(methods), borderaxespad=0)
        l_ax.set_axis_off()
        #for ax in axes[0,:]: ax.set_axis_off()        
                

def unique(seq):
    seen = set()
    seen_add = seen.add
    return [x for x in seq if not (x in seen or seen_add(x))]        