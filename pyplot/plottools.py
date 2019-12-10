import matplotlib.dates as mpd
import pandas as pd
import math

def setup_x_axis(ax, x, time=True, div=16):
    ax.set_xlim(x[0],x[-1])
    if time:
        ax.xaxis_date()
        hours = mpd.HourLocator(interval = max(1, len(x)//div))
        formatter = mpd.DateFormatter("%H:%M")
        ax.xaxis.set_major_locator(hours)
        ax.xaxis.set_major_formatter(formatter)
        
def make_square(x=None, y=None):
    if isinstance(x, (pd.DataFrame, pd.Series)):
        y = x.values
        x = x.index
    if not x is None:
        x2 =  sum([[b, b] for b in x], [])[1:]
    else: x2 = None
    if not y is None:
        y2 = sum([[b, b] for b in y], [])[:-1]
    else: y2 = None
    return (x2, y2)

def latexify(fig_width=None, fig_height=None, aspect=(math.sqrt(5)-1.0)/2.0, columns=1):
    """Set up matplotlib's RC params for LaTeX plotting.
    Call this before plotting a figure.

    Parameters
    ----------
    fig_width : float, optional, inches
    fig_height : float,  optional, inches
    columns : {1, 2}
    """

    # code adapted from http://www.scipy.org/Cookbook/Matplotlib/LaTeX_Examples

    # Width and max height in inches for IEEE journals taken from
    # computer.org/cms/Computer.org/Journal%20templates/transactions_art_guide.pdf

    assert(columns in [1,2])

    if fig_width is None:
        #fig_width = 3.34 if columns==1 else 6.88 # width in inches
        fig_width = 252.0/72.27 if columns == 1 else 522.0/72.27 #Elsevier

    if fig_height is None:
        fig_height = fig_width*aspect

    MAX_HEIGHT_INCHES = 8.0
    if fig_height > MAX_HEIGHT_INCHES:
        print("WARNING: fig_height too large:" + fig_height + 
              "so will reduce to" + MAX_HEIGHT_INCHES + "inches.")
        fig_height = MAX_HEIGHT_INCHES

    return (fig_width, fig_height)

    mpl.rcParams.update(params)