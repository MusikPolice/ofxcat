# ofxcat
A command line utility that imports bank transactions in the `*.ofx` format and categorizes them.

## Using the application
### Importing Transactions
This utility can import any transactions that were exported from your banking institution in the Open Financial Exchange (OFX) format.

The method for exporting transactions differs between each banking institution, but in general, the goal is to download an `*.ofx` file that contains transactions from all of your accounts.

On the website for my bank, the `*.ofx` format is referred to as "Money":
![Downloading transactions from RBC](images/download-transactions.jpg)

Once you have exported your transactions, the `.ofx` file can be imported with the `import` command:
```bash
java -jar ofxcat-1.0-SNAPSHOT-jar-with-dependencies.jar import mytransactions.ofx
``` 

`ofxcat` attempts to automatically categorize newly imported transactions based on their description (typically the name of the vendor that debited or credited your account).

#### Accounts
Each transaction belongs to exactly one account. Upon encountering an account for the first time, `ofxcat` will prompt you to give it a name:

![Adding a new account](images/ofxcat-new-account.png)

#### Transfers
Transfers between recognized accounts will be detected and automatically together based on the amount transferred and date of transfer:

![Inter-account transfers](images/ofxcat-inter-account-transfers.png)

#### Categorizing Transactions
When a new transaction is found that is not recognized as an inter-account transfer, `ofxcat` attempts to categorize it by searching for previously imported transactions that have the same description as the newly found transaction. If all exact matches belong to the same category, the new transaction is automatically added to that category. 

If no previously imported transactions have a description that exactly matches that of the new transaction, `ofxcat` splits the description of the new transaction up into tokens and searches for previously imported transactions with descriptions that contain some or all of those tokens. If any matches are found, `ofxcat` asks if you would like to add the new transaction to one of the categories that the matching transactions belong to.

![Prompted to choose from existing categories](images/ofxcat-prompted-to-choose-category.png)

If none of the proposed categories are suitable for the new transaction, `ofxcat` will allow you to choose from a list of all categories that it knows about, or to create a new category and add the new transaction to it.

Because transaction descriptions are compared using tokens and fuzzy string matching, `ofxcat` learns from patterns that it has seen in the past, and can categorize transactions based on vendor names that differ only slightly. As an example, if you typically do groceries at Megamart #123 but decide to shop at Megamart #125 while visiting a different city, `ofxcat` will recognize the similarity in the transaction descriptions and intuitively offer to categorize the transaction from Megamart #125 as `GROCERIES`.

### Reports
Once you have categorized some transactions, you can find out where your money is going by looking at how much is spent per category over a given period of time:
```bash
java -jar ofxcat-1.0-SNAPSHOT-jar-with-dependencies.jar get transactions --start-date=2022-11-01 --end-date=2022-06-30
```
This will return a matrix with category names along the x-axis and the months between the specified start and end days on the y-axis. The total amount spent in each category during each month appears in the table:
```
MONTH, DAYCARE, GROCERIES, MORTGAGE, PETS, PROPERTY TAX, RESTAURANTS, SHOPPING, UTILITIES, VEHICLES
Nov-21,-1899,-696.87,-1557.1,0,-285.61,-464.26,-293.41,-359,-284.19
Dec-21,-1493.58,-1263.73,-1557.1,-347.12,-285.6,-387.78,-433.53,-503.82,-421.78
Jan-22,-1388,-1359.73,-1557.1,-121.83,-283.91,-279.55,-949.85,-689.56,-718.9
Feb-22,-1388,-850.85,-1557.1,-95.81,-283.91,-478.64,-546,-531.98,-290.87
Mar-22,-1388,-846.14,-1557.1,-130.82,-283.91,-257.83,-1073.06,-640.24,-400.83
Apr-22,-1388,-994.94,-2335.65,-148.79,-283.91,-508.28,-356.43,-542.49,-319.47
May-22,-1325,-708.81,-778.55,0,-283.91,-243.57,-609.19,-543.29,-1249.54
Jun-22,0,0,0,0,0,0,0,0,0
p50,-1388,-850.85,-1557.1,-130.82,-283.91,-387.78,-546,-542.49,-400.83
p90,-1493.58,-1263.73,-1557.1,-148.79,-285.6,-478.64,-949.85,-640.24,-718.9
avg,-1467.08,-960.15,-1557.1,-168.87,-284.39,-374.27,-608.78,-544.34,-526.51
total,-10269.58,-6721.07,-10899.7,-844.37,-1990.76,-2619.91,-4261.47,-3810.38,-3685.58
```
The report is in csv format, and is designed to be copied into your spreadsheet application of choice.

## Storage and Logging
`ofxcat` stores imported transactions in an SQLite3 database located in `~/.ofx/ofxcat.db`. Similarly, the log file is located at `~/.ofx/ofxcat.log`.

Due to the nature of the application, both files may include sensitive information about your transaction history, and should be appropriately protected.

### Getting Help
If you get stuck, check the docs:
```bash
java -jar ofxcat-1.0-SNAPSHOT-jar-with-dependencies.jar help
```
If that doesn't work, create an issue, and I'll do my best to help you out as soon as possible.

## Building the application
To build ofxcat, you'll need the following software:
* git
* JDK 25+

Start by cloning the `master` branch of this repository:
```bash
git clone https://github.com/MusikPolice/ofxcat.git
cd ofxcat
```
Build with maven:
```bash
./gradlew clean build shadowJar
```

A fat jar that contains the application and its dependencies will be created at `build/libs/ofxcat-1.0-SNAPSHOT-jar-with-dependencies.jar`. 
You can run the application with:
```bash
java -jar build/libs/ofxcat-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Contributing

### Adding support for a new institution
While most financial institutions are capable of exporting `*.ofx` files, the quality of the data in those files differs between institutions, and every bank produces data with particular quirks. 

`ofxcat` can import any valid `*.ofx` data, but will work better if that data is cleaned up before processing.

Out of the box, Royal Bank of Canada (RBC) is the only institution that is officially supported. If you bank with a different institution and want to add support for it to ofxcat, follow the steps below:

1. **Obtain an `*.ofx` file:** Before you can contribute, you'll need to figure out how to export some transactions from your financial institution. If you're already using ofxcat, you should be familiar with this process.
2. **Determine your institution's bankId:** Open the `*.ofx` file in a text editor and search for `<BANKID>`. The value to the right of this string is your bank's unique identifier. An `*.ofx` file from RBC contains the string `<BANKID>900000100`, so RBC's bankId is `900000100`.
3. **Implement a TransactionCleaner:** Fork the code and add an implementation of the [TransactionCleaner](https://github.com/MusikPolice/ofxcat/blob/master/src/main/java/ca/jonathanfritz/ofxcat/cleaner/TransactionCleaner.java) interface to the `ca.jonathanfritz.ofxcat.cleaner` package. Because the data from each bank is different, you'll need to play with exports from your bank and do some trial and error to figure out how best to massage the data in your `*.ofx` files. [RbcTransactionCleaner](https://github.com/MusikPolice/ofxcat/blob/master/src/main/java/ca/jonathanfritz/ofxcat/cleaner/RbcTransactionCleaner.java) provides a good example of the type of data transformation that you may want to do. 
4. **Submit a pull request:** Please make sure that your code follows the standards and formatting that you see elsewhere in the project, and that it is unit tested.   