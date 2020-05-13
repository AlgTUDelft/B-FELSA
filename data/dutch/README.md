# Dutch scenario data #
This folder contains scenario data from the Dutch electricity market (TenneT). The data is based on the months April and May of 2010-2018. Every scenario is one week long. The original data set consists of nine years and from every year eight weeks from April and May are taken. This data is reformatted to make 72 'hypothetical' scenario's of one week long, that all start at 04/04/2016 and end at 10/04/2016.

Reserve usage data is obtained by finding the percentage of a Programme Time Unit (PTU) that reserves are used. Missing data is appended by drawing randomly from the available data.

The data was downloaded by with a script. This script is available on request.

## Description of the files ##
* `price_up/down.csv`: the reserve prices up and down. (The imbalance price is chosen to be either the down or up reserve price depending on which reserve usage is higher).
* `da.csv`: the day ahead market prices (one scenario, from 2016).
* `minute.csv`: per minute price and reserve data for April 2016, for realistic simulation. Only one scenario.
* `ptu_up/down.csv`: the reserve usage up and down.
* `AP_up/down.csv` and `ER_up/down.csv`. These files are used by the Linear Approximation method. They contain the coefficients for the piece-wise linear functions that describe the Expected Return (ER) and the Acceptance Probability (AP). These values are obtained by a script. The script is available on request.

## References ##
1. TenneT, Market information. http://www.tennet.org/bedrijfsvoering/ExporteerData.aspx, 2019, Accessed: 2019-07-03.
