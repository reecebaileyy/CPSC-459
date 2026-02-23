#!/bin/bash

javac -cp "scroogeCoinGrader.jar:rsa.jar:algs4.jar:." TestTxHandler.java TestMaxFeeTxHandler.java

if [ $? -eq 0 ]; then
    java -cp "scroogeCoinGrader.jar:rsa.jar:algs4.jar:." TestTxHandler

fi