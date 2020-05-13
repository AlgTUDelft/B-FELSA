# Dutch data 2016 #
This folder contains data from TenneT for the whole year 2016. The data consists of one scenario. Reserve usage data is obtained by finding the percentage of a Programme Time Unit (PTU) that reserves are used. Missing data is appended by drawing randomly from the available data.

The data was downloaded by with a script. This script is available on request.

## Description of the files ##
* `price_up/down.csv`: the reserve prices up and down. (The imbalance price is chosen to be either the down or up reserve price depending on which reserve usage is higher).
* `da.csv`: the day ahead market prices.
* `ptu_up/down.csv`: the reserve usage up and down.
* `markov_ptu_up/down_probs.txt`: These files describe the Markov transition probabilities (when divided by the row's sum) for reserve usage. This is used by the ARIMA scenario generator. The transition probabilities are based on the historic data. 

## References ##
1. TenneT, Market information. http://www.tennet.org/bedrijfsvoering/ExporteerData.aspx, 2019, Accessed: 2019-07-03.
