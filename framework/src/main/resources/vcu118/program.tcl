open_hw_manager
connect_hw_server
set hwtargets "localhost:3121/xilinx_tcf/Digilent/210308AC64BD"

set code [catch {
   	open_hw_target $hwtargets
} result]
if {$code == 0} {
	set device [lindex [get_hw_devices] 0]
	set_property PROGRAM.FILE [lindex $argv 0] $device
	set_property PROBES.FILE {} $device
	program_hw_devices $device
	close_hw_target
	disconnect_hw_server
	close_hw_manager
	exit
} else {
	puts ""
	close_hw_target
}