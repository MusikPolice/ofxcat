# ofxcat
A command line utility that imports bank transactions in `*.ofx` format and categorizes them.

## Building the application
To build ofxcat, you'll need the following software:
* git
* maven
* JDK 12+

Start by cloning the `master` branch of this repository:
```bash
git clone https://github.com/MusikPolice/ofxcat.git
cd ofxcat
```
Build with maven:
```bash
mvn clean install
```

## Importing Transactions
This utility can import any transactions that were exported from your banking institution in the Open Financial Exchange (OFX) format.

The method for exporting transactions differs between each banking institution, but in general, the goal is to download an `*.ofx` file that contains transactions from all of your accounts. 
![Downloading transactions from RBC](images/download-transactions.jpg) 

Once you have exported your transactions, the `.ofx` file can be imported from the command line:
```bash
java -jar ofxcat-1.0-SNAPSHOT-jar-with-dependencies.jar --file mytransactions.ofx
``` 