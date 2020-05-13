# Data description #
This folder contains the data files for B-FELSA.

This folder's setup is as follows:
* `dutch`: this folder contains scenario data from the Dutch electricity market (TenneT). The data is based on the months April and May of 2010-2018. This data is reformatted to make 72 'hypothetical' scenario's of one week long.
* `dutch1`: this folder contains data from TenneT for the whole year 2016. The data consists of one scenario.
* `ercot`: this folder contains scenario data from the Electric Reliability Council of Texas electricity (ERCOT). The data is based on data from 2015-2016. This data is reformatted to make 52 'hypothetical' scenario's of five days long.
* `loads`: this folder contains a number of files describing electric loads. This data is obtained from the Dundee city council.
* `config1.ini` and `config2.ini`: these two files are two example configuration files that show some of the use cases of B-FELSA.
* `grid.csv`: this file can be used to describe the amount of remaining grid capacity is available at nodes at different points in time. The data in this file is dummy data.

Further information about the files in the folder can be found in the folder.

## References ##
1. Dundee City Council, Electric vehicle charging sessions, https://data.dundeecity.gov.uk/dataset/ev-charging-data, 2018, Accessed: 2018-03-21
2. ERCOT, Market information, http://www.ercot.com/mktinfo/, 2016-2017, Accessed: 2018-02-27.
3. ERCOT, Fast responding regulation service pilot, http://www.ercot.com/mktrules/pilots/frrs, 2016, Accessed: 2018-03-14.
4. TenneT, Market information. http://www.tennet.org/bedrijfsvoering/ExporteerData.aspx, 2019, Accessed: 2019-07-03.
