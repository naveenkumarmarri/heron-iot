/* -*- P4_16 -*- */
#include <core.p4>
#include <v1model.p4>

const bit<16> ETHERTYPE_ARP  = 0x0806;
const bit<16> ETHERTYPE_IPV4 = 0x0800;

/*************************************************************************
*********************** H E A D E R S  ***********************************
*************************************************************************/

typedef bit<9>  egressSpec_t;
typedef bit<48> macAddr_t;
typedef bit<32> ip4Addr_t;

header ethernet_t {
    macAddr_t dstAddr;
    macAddr_t srcAddr;
    bit<16>   etherType;
}

header ipv4_t {
    bit<4>    version;
    bit<4>    ihl;
    bit<8>    diffserv;
    bit<16>   totalLen;
    bit<16>   identification;
    bit<3>    flags;
    bit<13>   fragOffset;
    bit<8>    ttl;
    bit<8>    protocol;
    bit<16>   hdrChecksum;
    ip4Addr_t srcAddr;
    ip4Addr_t dstAddr;
}

const bit<16> ARP_HTYPE_ETHERNET = 0x0001;
const bit<16> ARP_PTYPE_IPV4     = 0x0800;
const bit<8>  ARP_HLEN_ETHERNET  = 6;
const bit<8>  ARP_PLEN_IPV4      = 4;
const bit<16> ARP_OPER_REQUEST   = 1;
const bit<16> ARP_OPER_REPLY     = 2;
const bit<16> UDP_DST_PORT	 = 44000;
const bit<8> IPV4_UDP_PROTOCOL	 = 17;

typedef bit<4> cmd_count_t;

header arp_t {
    bit<16> htype;
    bit<16> ptype;
    bit<8>  hlen;
    bit<8>  plen;
    bit<16> oper;
}

header arp_ipv4_t {
    macAddr_t sha;
    ip4Addr_t spa;
    macAddr_t tha;
    ip4Addr_t tpa;
}
header midi_cmd_t {
	bit<32> time;
	bit<4> status;
	bit<4> channel;
	bit<8> pitch;
	bit<8> velocity;
}
header udp_t {
    bit<16>	 srcPort;
    bit<16>      dstPort;
    bit<16>      length;
    bit<16>	 checksum;
}
header midi_t {
	bit<1> b;
	bit<1> j;
	bit<1> z;
	bit<1> p;
	bit<4> len;
}
header rtp_t {
	bit<2> version;
	bit<1> padding;
	bit<1> extension;
	bit<4> csrc;
	bit<1> marker;
	bit<7> payload_type;
	bit<16> sequence_number;
	bit<32>	timestamp;
	bit<32> ssrc;
}

struct metadata {
    ip4Addr_t    dst_ipv4;
    macAddr_t    mac_da;
    macAddr_t    mac_sa;
    egressSpec_t egress_port;
    cmd_count_t	 cmd_count;
	
}

struct headers {
    ethernet_t   ethernet;
    arp_t        arp;
    arp_ipv4_t   arp_ipv4;
    ipv4_t       ipv4;
    udp_t	 udp;
    rtp_t	 rtp;
    midi_t	 midi;
    midi_cmd_t[15]	 cmd;
}

/*************************************************************************
*********************** P A R S E R  ***********************************
*************************************************************************/

parser MyParser(packet_in packet,
                out headers hdr,
                inout metadata meta,
                inout standard_metadata_t standard_metadata) {

    state start {
        transition parse_ethernet;
    }

    state parse_ethernet {
        packet.extract(hdr.ethernet);
        transition select(hdr.ethernet.etherType) {
            ETHERTYPE_IPV4: parse_ipv4;
	    ETHERTYPE_ARP: parse_arp;
            default: accept;
        }
    }

    state parse_ipv4 {
        packet.extract(hdr.ipv4);
	meta.dst_ipv4 = hdr.ipv4.dstAddr;
	transition select(hdr.ipv4.protocol) {
		IPV4_UDP_PROTOCOL:parse_udp;
		default:accept;
	}
    }

    state parse_arp {
    	/* 
	* TODO: parse ARP. If the ARP protocol field is
	* IPV4, transtion to  parse_arp_ipv4
	*/
	packet.extract(hdr.arp);
	 transition select(hdr.arp.htype) {
            (ARP_HTYPE_ETHERNET) : 
	    parse_arp_ipv4;
            default : accept;
        }
    }

    state parse_arp_ipv4 {
    	/* 
	* TODO: parse ARP_IPV4. Hint: one
	* possible solution is to store the 
	* target packet address in a meta data
	* field when parsing.
	*/
	 packet.extract(hdr.arp_ipv4);
        meta.dst_ipv4 = hdr.arp_ipv4.tpa;
	meta.mac_sa = hdr.arp_ipv4.sha;
        transition parse_udp;
    }
    state parse_udp {
    	packet.extract(hdr.udp);
	hdr.udp.checksum = 0;
	transition select(hdr.udp.dstPort) {
		(UDP_DST_PORT) : parse_rtp;
		default : accept;
	}
    }
    state parse_rtp {
    	packet.extract(hdr.rtp);
	transition parse_midi;
    }
    state parse_midi {
    	packet.extract(hdr.midi);
	meta.cmd_count = hdr.midi.len;
	transition parse_midi_cmd;
    }
    state parse_midi_cmd {
    	packet.extract(hdr.cmd.next);
	meta.cmd_count = meta.cmd_count - 1;
	transition select(meta.cmd_count) {
		0: accept;
		_: parse_midi_cmd;
	}
    
    }
}

/*************************************************************************
************   C H E C K S U M    V E R I F I C A T I O N   *************
*************************************************************************/

control MyVerifyChecksum(inout headers hdr, inout metadata meta) {   
    apply {  }
}


/*************************************************************************
**************  I N G R E S S   P R O C E S S I N G   *******************
*************************************************************************/

control MyIngress(inout headers hdr,
                  inout metadata meta,
                  inout standard_metadata_t standard_metadata) {

    action drop() {
        mark_to_drop();
    }

    /* TODO: Define actions and tables here */

    action set_dst_info(macAddr_t dstAddr, egressSpec_t port) {
     	/* 
	* TODO: add logic to store mac addresses and
	 * egress ports in meta data
	*/
	meta.mac_da      = dstAddr;
        meta.egress_port = port;
    }

    action  udp_forward() {
        hdr.ethernet.dstAddr = meta.mac_da;
        hdr.ethernet.srcAddr = meta.mac_sa;
        hdr.ipv4.ttl         = hdr.ipv4.ttl - 1;
	hdr.udp.checksum = 0;
	 standard_metadata.egress_spec = meta.egress_port;
    }
    action forward_ipv4() {
        hdr.ethernet.dstAddr = meta.mac_da;
        hdr.ethernet.srcAddr = meta.mac_sa;
        hdr.ipv4.ttl         = hdr.ipv4.ttl - 1;
        
        standard_metadata.egress_spec = meta.egress_port;
    }
    action forward_arp() {
       hdr.ethernet.dstAddr = meta.mac_da;
       hdr.ethernet.srcAddr = meta.mac_sa;

        standard_metadata.egress_spec = meta.egress_port;
    }
    action midi_modify() {
    	hdr.udp.checksum = 0;
	hdr.cmd[0].pitch = hdr.cmd[0].pitch - 20;
	hdr.cmd[0].velocity = hdr.cmd[0].velocity + 20;

      hdr.cmd[1].pitch = hdr.cmd[1].pitch - 20;
	hdr.cmd[1].velocity = hdr.cmd[1].velocity + 20;

      hdr.cmd[2].pitch = hdr.cmd[2].pitch - 20;
	hdr.cmd[2].velocity = hdr.cmd[2].velocity + 20;

      hdr.cmd[3].pitch = hdr.cmd[3].pitch - 20;
	hdr.cmd[3].velocity = hdr.cmd[3].velocity + 20;

      hdr.cmd[4].pitch = hdr.cmd[4].pitch - 20;
	hdr.cmd[4].velocity = hdr.cmd[4].velocity + 20;

      hdr.cmd[5].pitch = hdr.cmd[5].pitch - 20;
	hdr.cmd[5].velocity = hdr.cmd[5].velocity + 20;

      hdr.cmd[6].pitch = hdr.cmd[6].pitch - 20;
	hdr.cmd[6].velocity = hdr.cmd[6].velocity + 20;

      hdr.cmd[7].pitch = hdr.cmd[7].pitch - 20;
	hdr.cmd[7].velocity = hdr.cmd[7].velocity + 20;

      hdr.cmd[8].pitch = hdr.cmd[8].pitch - 20;
	hdr.cmd[8].velocity = hdr.cmd[8].velocity + 20;

      hdr.cmd[9].pitch = hdr.cmd[9].pitch - 20;
	hdr.cmd[9].velocity = hdr.cmd[9].velocity + 20;

      hdr.cmd[10].pitch = hdr.cmd[10].pitch - 20;
	hdr.cmd[10].velocity = hdr.cmd[10].velocity + 20;

      hdr.cmd[11].pitch = hdr.cmd[11].pitch - 20;
	hdr.cmd[11].velocity = hdr.cmd[11].velocity + 20;

      hdr.cmd[12].pitch = hdr.cmd[12].pitch - 20;
	hdr.cmd[12].velocity = hdr.cmd[12].velocity + 20;

      hdr.cmd[13].pitch = hdr.cmd[13].pitch - 20;
	hdr.cmd[13].velocity = hdr.cmd[13].velocity - 20;

      hdr.cmd[14].pitch = hdr.cmd[14].pitch - 20;
	hdr.cmd[14].velocity = hdr.cmd[14].velocity - 20;

    }
    table ipv4_lpm {
        key = {
            meta.dst_ipv4: lpm;
        }
        actions = {
            set_dst_info;
            drop;
            NoAction;
        }
        size = 1024;
        default_action = NoAction();
    }
    table midi_change {
    	key = {
		hdr.midi.z	:exact;
	}
	actions = {
		midi_modify;
		drop;
	}
	const default_action = drop();
	const entries = {
		(0)	:midi_modify();
	}
    }
    table arp_fwd {
        key = {
            hdr.arp.isValid()      : exact;
            hdr.arp.oper           : ternary;
            hdr.arp_ipv4.isValid() : exact;
            hdr.ipv4.isValid()     : exact;
	    hdr.rtp.isValid()	   : exact;
	}
        actions = {
	    set_dst_info;
            forward_arp;
	    forward_ipv4;
	    udp_forward;
            drop;
        }
        const default_action = drop();
        const entries = {
            ( true, _, true, false ,false) :
                                                         forward_arp();
            ( false, _, false, true  ,false) :
                                                         forward_ipv4();
	    (false, _, false, true, true) :	
	    						udp_forward();
        } 
    }

    apply {
        ipv4_lpm.apply();
	/* TODO: add another table to handle
	  * ipv4 and arp forwarding
	*/
	arp_fwd.apply();
	midi_change.apply();
    }
}

/*************************************************************************
****************  E G R E S S   P R O C E S S I N G   *******************
*************************************************************************/

control MyEgress(inout headers hdr,
                 inout metadata meta,
                 inout standard_metadata_t standard_metadata) {
    apply {  }
}

/*************************************************************************
*************   C H E C K S U M    C O M P U T A T I O N   **************
*************************************************************************/

control MyComputeChecksum(inout headers  hdr, inout metadata meta) {
     apply {
	update_checksum(
	    hdr.ipv4.isValid(),
            { hdr.ipv4.version,
	      hdr.ipv4.ihl,
              hdr.ipv4.diffserv,
              hdr.ipv4.totalLen,
              hdr.ipv4.identification,
              hdr.ipv4.flags,
              hdr.ipv4.fragOffset,
              hdr.ipv4.ttl,
              hdr.ipv4.protocol,
              hdr.ipv4.srcAddr,
              hdr.ipv4.dstAddr },
            hdr.ipv4.hdrChecksum,
            HashAlgorithm.csum16);
    }
}

/*************************************************************************
***********************  D E P A R S E R  *******************************
*************************************************************************/

control MyDeparser(packet_out packet, in headers hdr) {
    apply {
    	/* TODO: handle ARP! */

	
	 packet.emit(hdr.ethernet);
	 packet.emit(hdr.ipv4);
	 packet.emit(hdr.arp);
       packet.emit(hdr.arp_ipv4); 
	packet.emit(hdr.udp);
	packet.emit(hdr.rtp);
	packet.emit(hdr.midi);
	packet.emit(hdr.cmd);
    }
}

/*************************************************************************
***********************  S W I T C H  *******************************
*************************************************************************/

V1Switch(
MyParser(),
MyVerifyChecksum(),
MyIngress(),
MyEgress(),
MyComputeChecksum(),
MyDeparser()
) main;
