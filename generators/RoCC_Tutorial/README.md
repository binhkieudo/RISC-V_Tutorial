# Tutorial 1: A Simple RoCC Interface

This tutorial shows how to deploy the simple-example to chipyard framework.

This example illustrates how to use RoCCIO to communicate with a tightly-coupled accelerator.

In this example, the accelerator is a Block Memory (BRAM) which is declared by Verilog. FPU and Memory interface are ignored.

<img src="https://github.com/binhkieudo/RoCC_Tutorial/assets/22954544/eebf9387-0c67-453c-b4d4-0b640c5ead10" alt="drawing" width="600"/>

The test software will write data to RoCC-BRAM and then read back.

**Step 1:** Clone the tutorial into your working folder.

Assume that you are being in chipyard's folder.

```shell
source env.sh
cd generators
git clone https://github.com/binhkieudo/RoCC_Tutorial.git
```

**Step 2:** Setup the example to work with chipyard's framework.

```shell
cd RoCC_Tutorial
sudo chmod a+x ./setup.sh
./setup.sh 1
```

**Step 3:** Prepare the the software
From RoCC_Tutorial folder...

```shell
cd ./simple-example/software
make
```
The produced tut01.riscv binary file will be used to test the hardware.

**Step 4:** Simulate the hardware with the compiled binary file

Assume that you are being in chipyard's folder.

```shell
cd ./sims/verilator
make run-binary CONFIG=Tutorial1RocketConfig BINARY=../../generators/RoCC_Tutorial/simple-example/software/tut01.riscv LOADMEM=1 
```
