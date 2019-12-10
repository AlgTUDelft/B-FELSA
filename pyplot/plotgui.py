from tkinter import filedialog, ttk
import tkinter as tk
import os
import sys
from experiment import Experiment
from experiment_plot import ExperimentPlot
import matplotlib as mpl
import matplotlib.pyplot as plt
import seaborn as sns


class MainFrame(tk.Frame):
    def __init__(self, master, folder=None):
        super(MainFrame, self).__init__(master)
        self.master = master
        master.title("Flexible Loads Toolbox - Results")
        
        self.plotframe = PlotFrame(self)
        self.plotframe.pack(fill=tk.BOTH, expand=True)
        
        self.close_button = tk.Button(self, text="Close", command=master.quit)
        self.close_button.pack(side=tk.RIGHT)
        
        self.select_experiment_button = tk.Button(self, text="Select Experiment", command=self.select_experiment)
        self.select_experiment_button.pack(side=tk.RIGHT)
                
        self.pack(fill=tk.BOTH, expand=True)
        
        if not folder is None:
            self.plotframe.set_filename(folder)

    def select_experiment(self):
        path = os.path.dirname(os.getcwd())
        if os.path.isdir(os.path.join(path, 'output')):
            path = os.path.join(path, 'output')
        filename = filedialog.askdirectory(initialdir=path, mustexist = True)
        if filename == '' or filename == (): return
        if not os.path.isfile(os.path.join(filename, 'experiment.ini')): return
        self.plotframe.set_filename(filename)
    
class PlotFrame(tk.Frame):
    def __init__(self, master):
        super(PlotFrame, self).__init__(master)
        self.master = master
        
        self.tab_control = ttk.Notebook(self, width=200, height=100)        
        self.tab_control.pack(fill=tk.BOTH, expand=True)
        
        self.title_label = tk.Label(self, text='<please choose an experiment folder>')
        self.title_label.pack(fill=tk.BOTH, expand=False)
        
        self.pack(fill=tk.BOTH, expand=True)
    
    def set_filename(self, filename):
        self.title_label['text'] = os.path.basename(filename)
        self.experiment = Experiment.get_experiment(filename)
        frames = ExperimentPlot.get_all_frames(self.experiment, self.tab_control)
        if len(frames) == 0: return
        for tab in self.tab_control.tabs():
            self.tab_control.forget(tab)
        for p in frames:
            if p is None or p[0] is None: continue
            self.tab_control.add(p[0], text=p[1])

class Plot(tk.Frame):
    def __init__(self, master):
        super(Plot, self).__init__(master)
        self.master = master

ExperimentPlot.paper = False
if ExperimentPlot.paper:
    mpl.rcParams['savefig.format'] = 'pdf'
    mpl.rcParams['font.family'] = ['serif']
else:
    plt.style.use('ggplot')

root = tk.Tk()
root.protocol("WM_DELETE_WINDOW", root.quit)
root.geometry("800x600+300+300")
if len(sys.argv) > 1: my_gui = MainFrame(root, sys.argv[1])
else: my_gui = MainFrame(root)

root.mainloop()