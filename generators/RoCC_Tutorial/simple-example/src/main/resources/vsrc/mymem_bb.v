module mymem_bb (
    input           clock,
    input           rqvalid,
    input [4:0]     rqaddr,
    input           wren,
    input [9:0]     addr,
    input [63:0]    wrdata,
    output          rdvalid,
    output [4:0]    rdaddr,
    output [63:0]   rddata
);

    reg [63:0] mem [0:1023];

    reg        r_rdvalid;
    reg [4:0]  r_rdaddr;
    reg [63:0] r_rddata;

    always @(posedge clock) begin
        if (wren) mem[addr] <= wrdata;
        r_rdvalid   <= rqvalid;
        r_rdaddr    <= rqaddr;
        r_rddata    <= mem[addr];
    end

    assign rdvalid  = r_rdvalid;
    assign rdaddr   = r_rdaddr;
    assign rddata   = r_rddata;

endmodule
