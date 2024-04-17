#!/usr/bin/bash

ROOTDIR=../..
ROOTFILE=$ROOTDIR/build.sbt
ORIGDIR=./orig
ORIGFILE=$ORIGDIR/build.sbt
TMPFILE=./build.sbt

CONFIGDIR=../chipyard/src/main/scala/config/

# Backup the original file
mkdir -p $ORIGDIR
cp -rn $ROOTFILE $ORIGFILE

usage() {
    echo "Usage: ${0} [OPTIONS]"
    echo "Options"
    echo "  --help -h       : Display this message"
    echo "  --remove -r     : Remove the setup"
    echo "  --update -u     : Update backup file"
    echo "  1               : Setup Tutorial 1: Rocc simple example"
    echo "  0               : Setup all"
    exit "$1"
}

function setup_tut1
{
    tmp=$(grep "tut_1" $TMPFILE)
    if [ "$tmp" == "" ]; then
        awk 'NR==1,/sha3/{sub(/sha3/, "sha3,\n\t\ttut_1")} 1' $TMPFILE> ./tmp.sbt
        cat ./simple-example/script >> ./tmp.sbt
        cat ./tmp.sbt > $TMPFILE
        rm -f ./tmp.sbt
        cp ./simple-example/RoCCTutorialConfig $CONFIGDIR/RoCCTutorialConfig.scala
    fi
}

function setup_tut2
{
    echo "Tu1 2"
}

while [ "$1" != "" ];
do
    case $1 in
        -h | --help)
            usage 3 ;;
        -r | --remove)
            cp $ORIGFILE $ROOTFILE ;;
        -u | --update)
            cp $ROOTFILE $ORIGFILE ;;
        1)
            cp $ORIGFILE $TMPFILE
            setup_tut1
            cat $TMPFILE > $ROOTFILE
            rm -f $TMPFILE
            echo "[Tut01] Setup completed!"
            break
            ;;
    esac
done



