#########################################################################################
# fpga prototype makefile
#########################################################################################

#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ..)
sim_dir=$(abspath .)

# do not generate simulation files
sim_name := none

#########################################################################################
# include shared variables
#########################################################################################
SUB_PROJECT ?= vc707_rocketsmall

#########################################################################################
# My configuration
#########################################################################################
ifeq ($(SUB_PROJECT),arty100t_rocketsmall)
	# TODO: Fix with Arty
	SBT_PROJECT       ?= framework
	MODEL             ?= Arty100THarness
	VLOG_MODEL        ?= Arty100THarness
	MODEL_PACKAGE     ?= framework.fpga.arty100t
	CONFIG            ?= SmallRocketArty100TConfig
	CONFIG_PACKAGE    ?= framework.fpga.arty100t
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             ?= arty_a7_100
	FPGA_BRAND        ?= xilinx
endif

ifeq ($(SUB_PROJECT),vc707_rocketsmall)
	SBT_PROJECT       ?= framework
	MODEL             ?= VC707Harness
	VLOG_MODEL        ?= VC707Harness
	MODEL_PACKAGE     ?= framework.fpga.vc707
	CONFIG            ?= SmallRocketVC707Config
	CONFIG_PACKAGE    ?= framework.fpga.vc707
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             ?= vc707
	FPGA_BRAND        ?= xilinx
endif

################### End my configurations #################################################

include $(base_dir)/variables.mk

# default variables to build the arty example
# setup the board to use

.PHONY: default
default: $(mcs)

#########################################################################################
# misc. directories
#########################################################################################
fpga_dir := $(base_dir)/fpga/fpga-shells/$(FPGA_BRAND)
fpga_common_script_dir := $(fpga_dir)/common/tcl

#########################################################################################
# setup misc. sim files
#########################################################################################
# copy files but ignore *.h files in *.f (match vcs)
$(sim_files): $(SIM_FILE_REQS) $(ALL_MODS_FILELIST) | $(GEN_COLLATERAL_DIR)
	-cp -f $(SIM_FILE_REQS) $(GEN_COLLATERAL_DIR)
	touch $@
	$(foreach file,\
		$(SIM_FILE_REQS),\
		$(if $(filter %.h,$(file)),\
			,\
			echo "$(addprefix $(GEN_COLLATERAL_DIR)/, $(notdir $(file)))" >> $@;))

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# copy from other directory
#########################################################################################
all_vsrcs := \
	$(base_dir)/generators/sifive-blocks/vsrc/SRLatch.v

#########################################################################################
# vivado rules
#########################################################################################
# combine all sources into single .f
synth_list_f := $(build_dir)/$(long_name).vsrcs.f
$(synth_list_f): $(sim_common_files) $(all_vsrcs)
	$(foreach file,$(all_vsrcs),echo "$(file)" >> $@;)
	cat $(sim_common_files) >> $@

BIT_FILE := $(build_dir)/obj/$(MODEL).bit
$(BIT_FILE): $(synth_list_f)
	cd $(build_dir); vivado \
		-nojournal -mode batch \
		-source $(fpga_common_script_dir)/vivado.tcl \
		-tclargs \
			-top-module "$(MODEL)" \
			-F "$(synth_list_f)" \
			-board "$(BOARD)" \
			-ip-vivado-tcls "$(shell find '$(build_dir)' -name '*.vivado.tcl')"

.PHONY: bitstream
bitstream: clean_cache $(BIT_FILE)

.PHONY: debug-bitstream
debug-bitstream: $(build_dir)/obj/post_synth.dcp
	cd $(build_dir); vivado \
		-nojournal -mode batch \
		-source $(sim_dir)/scripts/run_impl_bitstream.tcl \
		-tclargs \
			$(build_dir)/obj/post_synth.dcp \
			$(BOARD) \
			$(build_dir)/debug_obj \
			$(fpga_common_script_dir)

.PHONY: download_bitstream
download_bitstream:
	if [ $(BOARD) = arty_a7_100 ]; then \
		vivado -mode batch -nolog -nojournal -notrace \
			-source $(sim_dir)/src/main/resources/arty100t/program.tcl \
			-tclargs $(build_dir)/obj/$(MODEL).bit; \
	else \
		echo "Not defined"; \
    fi

.PHONY: clean_cache
clean_cache:
	rm $(base_dir)/.classpath_cache/*

.PHONY: everything
everything: bitstream download_bitstream

#########################################################################################
# general cleanup rules
#########################################################################################
.PHONY: clean
clean:
	rm $(base_dir)/.classpath_cache/*
	rm -rf $(gen_dir)
