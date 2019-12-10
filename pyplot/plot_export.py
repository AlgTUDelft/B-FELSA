import tkinter as tk
from plottools import latexify
import matplotlib.pyplot as plt
from tkdocviewer import DocViewer
import pickle
import io
from matplotlib.backends.backend_template import FigureCanvas
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from matplotlib.backends.backend_agg import FigureCanvasAgg
import math

class PlotExport(tk.Toplevel):
    
    def __init__(self, master, figure, savefig, *args):
        super(PlotExport, self).__init__(master)
        self.master = master
        self.org_figure = figure
        self.savefig = savefig
        self.bbox_extra_artists = []
        self.args = args
        self.filename = args[0]
        self.example = None
        self.entries = self.makeform([('Aspect', 0.5), ('Label size', 7), ('Tick size', 7), ('Legend size', 7), ('Title size', 7),
                                       ('Number of columns', 1), ('Legend position', 'lower center'), ('Legend x offset', 0), 
                                       ('Legend y offset', 0), ('Horizontal padding', 0.01), ('Vertical padding', 0.01),
                                      ('Title', ", ".join([ax.get_title() for ax in self.org_figure.get_axes()])),
                                      ('Enabled', ", ".join([str(ax.axison) for ax in self.org_figure.get_axes()])),
                                      ('Xlim left', ", ".join([str(ax.get_xlim()[0]) for ax in self.org_figure.get_axes()])),
                                      ('Xlim right', ", ".join([str(ax.get_xlim()[1]) for ax in self.org_figure.get_axes()])),
                                      ('Ylim down', ", ".join([str(ax.get_ylim()[0]) for ax in self.org_figure.get_axes()])),
                                      ('Ylim left', ", ".join([str(ax.get_ylim()[1]) for ax in self.org_figure.get_axes()])),
                                      ('Horizontal label', ", ".join([ax.get_xlabel() for ax in self.org_figure.get_axes()])),
                                      ('Vertical label', ", ".join([ax.get_ylabel() for ax in self.org_figure.get_axes()]))])
        b2 = tk.Button(self, text='Save', command=self.save_event)
        b2.pack(side=tk.LEFT, padx=5, pady=5)
        b3 = tk.Button(self, text='Quit', command=self.close)
        b3.pack(side=tk.LEFT, padx=5, pady=5)
        self.save_event()
    
    def close(self):
        self.destroy()
    
    def makeform(self, fields):
        entries = {}
        for field, value in fields:
            row = tk.Frame(self)
            lab = tk.Label(row, width=22, text=field+": ", anchor='w')
            ent = tk.Entry(row)
            ent.insert(0, str(value))
            row.pack(side=tk.TOP, 
                     fill=tk.X, 
                     padx=5, 
                     pady=5)
            lab.pack(side=tk.LEFT)
            ent.pack(side=tk.RIGHT, 
                     expand=tk.YES, 
                     fill=tk.X)
            entries[field] = ent
        return entries 
    
    def update_event(self, *args):
        ps = {
            "aspect": float(self.entries["Aspect"].get()),
            "labelsize": int(self.entries["Label size"].get()),
            "ticksize": int(self.entries["Tick size"].get()),
            "legendsize": int(self.entries["Legend size"].get()),
            "titlesize": int(self.entries["Title size"].get()),
            "legendpos": self.entries["Legend position"].get(),
            "legend_x_offset": float(self.entries["Legend x offset"].get()),
            "legend_y_offset": float(self.entries["Legend y offset"].get()),
            "wpad": float(self.entries["Horizontal padding"].get()),
            "hpad": float(self.entries["Vertical padding"].get()),
            "ncols": int(self.entries["Number of columns"].get()),
            "title": self.entries["Title"].get(),
            "enabled": list(map(lambda x: x=='True', self.entries["Enabled"].get().split(", "))),
            "xlim": list(zip(list(map(float, self.entries["Xlim left"].get().split(", "))),
                        list(map(float, self.entries["Xlim right"].get().split(", "))))),
            "ylim": list(zip(list(map(float, self.entries["Ylim down"].get().split(", "))),
                        list(map(float, self.entries["Ylim up"].get().split(", "))))),
            "xlabel": self.entries["Horizontal label"].get(),
            "ylabel": self.entries["Vertical label"].get()
        }
        with plt.style.context('seaborn-paper'):
            self.figure = plt.figure(figsize=plt.figaspect(ps['aspect']))
            self.org_figure.draw_figure(self.figure)
        self.bbox_extra_artists = []
        
        fig_w, fig_h = latexify(columns=ps['ncols'], aspect=ps['aspect'])
        self.figure.set_size_inches(fig_w, fig_h)
        self.figure.canvas.draw_idle()
        
        leg_handles, leg_labels = [], []
        for ax_i, ax in enumerate(self.figure.get_axes()):
            if ps['enabled'][ax_i]:
                ax.set_xlabel(ps['xlabel'].split(", ")[ax_i], fontsize=ps['labelsize'])
                ax.set_ylabel(ps['ylabel'].split(", ")[ax_i], fontsize=ps['labelsize'])
                ax.set_title(ps['title'].split(", ")[ax_i], fontsize=ps['titlesize'])
                ax.set_xlim(ps['xlim'][ax_i])
                ax.set_ylim(ps['ylim'][ax_i])
                ax.tick_params(labelsize=ps['ticksize'])
            else:
                ax.set_axis_off()            
            
            ##Update legend
            old_leg = ax.get_legend()
            
            if not old_leg is None:
                bb = old_leg.get_tightbbox(self.figure.canvas.get_renderer()).inverse_transformed(self.figure.transFigure)
                legend_w = bb.x1 - bb.x0
                legend_h = bb.y1 - bb.y0

                lines, labels = ax.get_legend_handles_labels()
                _ax_i=0
                while len(lines) == 0 and _ax_i < len(self.figure.get_axes()):
                    lines, labels = self.figure.get_axes()[_ax_i].get_legend_handles_labels()
                    _ax_i+=1
                ncol = old_leg._ncol
                borderaxespad = old_leg.borderaxespad
                old_leg.remove()
                position = {'best': ax.bbox, 
                            'upper right': (1.0+ps['legend_x_offset'], 1.0+ps['legend_y_offset']),
                            'upper left': (0.0+ps['legend_x_offset'], 1.0+ps['legend_y_offset']),
                            'lower left': (0.0+ps['legend_x_offset'], 0.0+ps['legend_y_offset']),
                            'lower right': (1.0+ps['legend_x_offset'], 0.0+ps['legend_y_offset']),
                            'right': (1.0+ps['legend_x_offset'], 0.5+ps['legend_y_offset']),
                            'center left': (0+ps['legend_x_offset'], 0.5+ps['legend_y_offset']),
                            'center right': (1.0+ps['legend_x_offset'], 0.5+ps['legend_y_offset']),
                            'lower center': (0.5+ps['legend_x_offset'], 0.0+ps['legend_y_offset']),
                            'upper center': (0.5+ps['legend_x_offset'], 1.0+ps['legend_y_offset']),
                            'center': (0.5+ps['legend_x_offset'], 0.5+ps['legend_y_offset'])
                    }
                new_legend = ax.legend(lines, labels, fontsize = ps['legendsize'], loc=ps['legendpos'], ncol=ncol, \
                                borderaxespad=borderaxespad,bbox_to_anchor=position[ps['legendpos']])
                self.bbox_extra_artists.append(new_legend)    

        self.figure.canvas.draw_idle()
        self.figure.tight_layout(pad=0.01, w_pad=ps['wpad'], h_pad=ps['hpad'])
    
    def save_event(self, *args):
        if not self.example is None: self.example.close_file()
        self.update_event(*args)
        self.savefig(self.figure, bbox_extra_artists=self.bbox_extra_artists, *self.args)#bbox_inches='tight', pad_inches=0.025, *self.args)
        if self.example is None:
            self.example = ExportPreview(self, self.filename)
        else:
            self.example.reopen_file()

class ExportPreview(tk.Toplevel):
    
    def __init__(self, master, doc):
        super(ExportPreview, self).__init__(master)
        self.master = master
        self.doc = doc
        self.viewer = DocViewer(self, enable_downscaling=True)
        self.viewer.pack(side="top", expand=1, fill="both")
        self.viewer.display_file(self.doc)
        
    def close_file(self):
        self.viewer.pack_forget()
        self.viewer.destroy()
        
    def reopen_file(self):
        self.viewer = DocViewer(self)
        self.viewer.pack(side="top", expand=1, fill="both")
        self.viewer.display_file(self.doc)