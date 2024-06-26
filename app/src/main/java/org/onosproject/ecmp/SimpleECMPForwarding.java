package org.onosproject.ecmp;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.onlab.packet.MacAddress;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ComponentPropertyType;

import org.apache.karaf.shell.api.action.lifecycle.Service;

import org.onlab.packet.*;
import org.onlab.util.Tools;
import org.onlab.util.ItemNotFoundException;

import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.Event;

import org.onosproject.net.*;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.srv6_usid.common.Srv6DeviceConfig;

import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.*;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by cr on 16-11-28.
 */
@Component(immediate = true)
@Service
public class SimpleECMPForwarding implements ECMPPathService {


    private static final int DEFAULT_TIMEOUT = 60;
    private static final int DEFAULT_PRIORITY = 10;

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private NetworkConfigService networkConfigService;

    //will not use
    //@Reference(cardinality = ReferenceCardinality.MANDATORY)
    //protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private DeviceService deviceService;

    //COMMENTED BECAUSE I DO NOT INTEND TO PROCESS EACH PACKET INDIVIDUALLY
    //private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    private ApplicationId appId;

    //@Property(name = "packetOutOnly", boolValue = false,
    //        label = "Enable packet-out only forwarding; default is false")
    private boolean packetOutOnly = false;

    //@Property(name = "packetOutOfppTable", boolValue = false,
    //        label = "Enable first packet forwarding using OFPP_TABLE port " +
    //                "instead of PacketOut with actual port; default is false")
    private boolean packetOutOfppTable = false;

    //@Property(name = "flowTimeout", intValue = DEFAULT_TIMEOUT,
    //        label = "Configure Flow Timeout for installed flow rules; " + "default is 10 sec")
    private int flowTimeout = DEFAULT_TIMEOUT;

    //@Property(name = "flowPriority", intValue = DEFAULT_PRIORITY,
    //        label = "Configure Flow Priority for installed flow rules; " + "default is 10")
    private int flowPriority = DEFAULT_PRIORITY;

    //@Property(name = "ipv6Forwarding", boolValue = false,
    //        label = "Enable IPv6 forwarding; default is false")
    private boolean ipv6Forwarding = false;

    //@Property(name = "matchDstMacOnly", boolValue = false,
    //        label = "Enable matching Dst Mac Only; default is false")
    private boolean matchDstMacOnly = false;

    //@Property(name = "matchVlanId", boolValue = false,
    //        label = "Enable matching Vlan ID; default is false")
    private boolean matchVlanId = false;

    //@Property(name = "matchIpv4Address", boolValue = true,
    //        label = "Enable matching IPv4 Addresses; default is false")
    private boolean matchIpv4Address = true;

    //@Property(name = "matchIpv4Dscp", boolValue = false,
    //        label = "Enable matching IPv4 DSCP and ECN; default is false")
    private boolean matchIpv4Dscp = false;

    //@Property(name = "matchIpv6Address", boolValue = false,
    //        label = "Enable matching IPv6 Addresses; default is false")
    private boolean matchIpv6Address = false;

    //@Property(name = "matchIpv6FlowLabel", boolValue = false,
    //        label = "Enable matching IPv6 FlowLabel; default is false")
    private boolean matchIpv6FlowLabel = false;

    //@Property(name = "matchTcpUdpPorts", boolValue = false,
    //        label = "Enable matching TCP/UDP ports; default is false")
    private boolean matchTcpUdpPorts = true;

    //@Property(name = "matchIcmpFields", boolValue = false,
    //        label = "Enable matching ICMPv4 and ICMPv6 fields; " + "default is false")
    private boolean matchIcmpFields = false;


    //@Property(name = "ignoreIPv4Multicast", boolValue = false,
    //        label = "Ignore (do not forward) IPv4 multicast packets; default is false")
    private boolean ignoreIpv4McastPackets = false;

    private final TopologyListener topologyListener = new InternalTopologyListener();  


    @Activate
    public void activate(ComponentContext context) {
        //will not use
        //cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("org.onosproject.ecmp");

        //COMMENTED BECAUSE I DO NOT INTEND TO PROCESS EACH PACKET INDIVIDUALLY
        //packetService.addProcessor(processor, PacketProcessor.director(4));
        topologyService.addListener(topologyListener);
        readComponentConfiguration(context);
        requestIntercepts();

        log.info("Started", appId.id());
    }

    @Deactivate
    public void deactivate() {
        //will not use
        //cfgService.unregisterProperties(getClass(), false);
        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);

        //COMMENTED BECAUSE I DO NOT INTEND TO PROCESS EACH PACKET INDIVIDUALLY
        //packetService.removeProcessor(processor);
        //processor = null;
        topologyService.removeListener(topologyListener);
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        readComponentConfiguration(context);
        requestIntercepts();
    }

    /**
     * Request packet in via packet service.
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

        selector.matchEthType(Ethernet.TYPE_IPV6);
        if (ipv6Forwarding) {
            packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        } else {
            packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        }
    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_IPV6);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Extracts properties from the component configuration context.
     *
     * @param context the component context
     */
    private void readComponentConfiguration(ComponentContext context) {
        Dictionary<?, ?> properties = context.getProperties();

        Boolean packetOutOnlyEnabled =
                Tools.isPropertyEnabled(properties, "packetOutOnly");
        if (packetOutOnlyEnabled == null) {
            log.info("Packet-out is not configured, " +
                    "using current value of {}", packetOutOnly);
        } else {
            packetOutOnly = packetOutOnlyEnabled;
            log.info("Configured. Packet-out only forwarding is {}",
                    packetOutOnly ? "enabled" : "disabled");
        }

        Boolean packetOutOfppTableEnabled =
                Tools.isPropertyEnabled(properties, "packetOutOfppTable");
        if (packetOutOfppTableEnabled == null) {
            log.info("OFPP_TABLE port is not configured, " +
                    "using current value of {}", packetOutOfppTable);
        } else {
            packetOutOfppTable = packetOutOfppTableEnabled;
            log.info("Configured. Forwarding using OFPP_TABLE port is {}",
                    packetOutOfppTable ? "enabled" : "disabled");
        }

        Boolean ipv6ForwardingEnabled =
                Tools.isPropertyEnabled(properties, "ipv6Forwarding");
        if (ipv6ForwardingEnabled == null) {
            log.info("IPv6 forwarding is not configured, " +
                    "using current value of {}", ipv6Forwarding);
        } else {
            ipv6Forwarding = ipv6ForwardingEnabled;
            log.info("Configured. IPv6 forwarding is {}",
                    ipv6Forwarding ? "enabled" : "disabled");
        }

        Boolean matchDstMacOnlyEnabled =
                Tools.isPropertyEnabled(properties, "matchDstMacOnly");
        if (matchDstMacOnlyEnabled == null) {
            log.info("Match Dst MAC is not configured, " +
                    "using current value of {}", matchDstMacOnly);
        } else {
            matchDstMacOnly = matchDstMacOnlyEnabled;
            log.info("Configured. Match Dst MAC Only is {}",
                    matchDstMacOnly ? "enabled" : "disabled");
        }

        Boolean matchVlanIdEnabled =
                Tools.isPropertyEnabled(properties, "matchVlanId");
        if (matchVlanIdEnabled == null) {
            log.info("Matching Vlan ID is not configured, " +
                    "using current value of {}", matchVlanId);
        } else {
            matchVlanId = matchVlanIdEnabled;
            log.info("Configured. Matching Vlan ID is {}",
                    matchVlanId ? "enabled" : "disabled");
        }

        Boolean matchIpv4AddressEnabled =
                Tools.isPropertyEnabled(properties, "matchIpv4Address");
        if (matchIpv4AddressEnabled == null) {
            log.info("Matching IPv4 Address is not configured, " +
                    "using current value of {}", matchIpv4Address);
        } else {
            matchIpv4Address = matchIpv4AddressEnabled;
            log.info("Configured. Matching IPv4 Addresses is {}",
                    matchIpv4Address ? "enabled" : "disabled");
        }

        Boolean matchIpv4DscpEnabled =
                Tools.isPropertyEnabled(properties, "matchIpv4Dscp");
        if (matchIpv4DscpEnabled == null) {
            log.info("Matching IPv4 DSCP and ECN is not configured, " +
                    "using current value of {}", matchIpv4Dscp);
        } else {
            matchIpv4Dscp = matchIpv4DscpEnabled;
            log.info("Configured. Matching IPv4 DSCP and ECN is {}",
                    matchIpv4Dscp ? "enabled" : "disabled");
        }

        Boolean matchIpv6AddressEnabled =
                Tools.isPropertyEnabled(properties, "matchIpv6Address");
        if (matchIpv6AddressEnabled == null) {
            log.info("Matching IPv6 Address is not configured, " +
                    "using current value of {}", matchIpv6Address);
        } else {
            matchIpv6Address = matchIpv6AddressEnabled;
            log.info("Configured. Matching IPv6 Addresses is {}",
                    matchIpv6Address ? "enabled" : "disabled");
        }

        Boolean matchIpv6FlowLabelEnabled =
                Tools.isPropertyEnabled(properties, "matchIpv6FlowLabel");
        if (matchIpv6FlowLabelEnabled == null) {
            log.info("Matching IPv6 FlowLabel is not configured, " +
                    "using current value of {}", matchIpv6FlowLabel);
        } else {
            matchIpv6FlowLabel = matchIpv6FlowLabelEnabled;
            log.info("Configured. Matching IPv6 FlowLabel is {}",
                    matchIpv6FlowLabel ? "enabled" : "disabled");
        }

        Boolean matchTcpUdpPortsEnabled =
                Tools.isPropertyEnabled(properties, "matchTcpUdpPorts");
        if (matchTcpUdpPortsEnabled == null) {
            log.info("Matching TCP/UDP fields is not configured, " +
                    "using current value of {}", matchTcpUdpPorts);
        } else {
            matchTcpUdpPorts = matchTcpUdpPortsEnabled;
            log.info("Configured. Matching TCP/UDP fields is {}",
                    matchTcpUdpPorts ? "enabled" : "disabled");
        }

        Boolean matchIcmpFieldsEnabled =
                Tools.isPropertyEnabled(properties, "matchIcmpFields");
        if (matchIcmpFieldsEnabled == null) {
            log.info("Matching ICMP (v4 and v6) fields is not configured, " +
                    "using current value of {}", matchIcmpFields);
        } else {
            matchIcmpFields = matchIcmpFieldsEnabled;
            log.info("Configured. Matching ICMP (v4 and v6) fields is {}",
                    matchIcmpFields ? "enabled" : "disabled");
        }

        Boolean ignoreIpv4McastPacketsEnabled =
                Tools.isPropertyEnabled(properties, "ignoreIpv4McastPackets");
        if (ignoreIpv4McastPacketsEnabled == null) {
            log.info("Ignore IPv4 multi-cast packet is not configured, " +
                    "using current value of {}", ignoreIpv4McastPackets);
        } else {
            ignoreIpv4McastPackets = ignoreIpv4McastPacketsEnabled;
            log.info("Configured. Ignore IPv4 multicast packets is {}",
                    ignoreIpv4McastPackets ? "enabled" : "disabled");
        }
        flowTimeout = Tools.getIntegerProperty(properties, "flowTimeout", DEFAULT_TIMEOUT);
        log.info("Configured. Flow Timeout is configured to {}", flowTimeout, " seconds");

        flowPriority = Tools.getIntegerProperty(properties, "flowPriority", DEFAULT_PRIORITY);
        log.info("Configured. Flow Priority is configured to {}", flowPriority);
    }

    /*
    * Receives 3 packet elements and returns the path between the the 2 indicated hosts
    * Only works for switch to switch paths
    * @param srcID, source ID
    * @param dstID, destination ID
    * @param flowLabel, flow label of the packet
    */
    @Override
    public Path getPath(ElementId srcID, ElementId dstID, int flowLabel) {
        //--------Calculate the path between the source and destination elements
        // Convert ElementId to DeviceId
        DeviceId srcDeviceId = (DeviceId) srcID;
        DeviceId dstDeviceId = (DeviceId) dstID;

        Topology currentTopology = topologyService.currentTopology();

        //log.info("Getting ECMP paths between {} and {}", srcDeviceId, dstDeviceId);
        if (currentTopology == null){
            log.info("Topology service is null");
            return null;
        }

        // Gets the shortest path between the source and destination (if a tie, returns them all paths)
        Set<Path> paths = topologyService.getPaths(currentTopology, srcDeviceId, dstDeviceId);
        if (paths.isEmpty()) { 
            log.info("No paths found between {} and {}", srcDeviceId, dstDeviceId);
            return null;
        }
        
        //print each link of each path
        /*paths.forEach(path -> {
            StringBuilder pathDescription = new StringBuilder("Path:\n");
            path.links().forEach(link -> {
                pathDescription.append(link.src()).append(" -> ").append(link.dst()).append("\n");
            });
            log.info(pathDescription.toString());
        });
        log.info("end paths prints");*/

        // Get the hash that will select this flow's path (switch's configured IPs) (ONLY works for switch to switch paths)
        final Ip6Address ip_src = getMySubNetIP(srcDeviceId);
        final Ip6Address ip_dst = getMySubNetIP(dstDeviceId);
        long ecmpCode = ecmpCode(ip_src, ip_dst, flowLabel);

        //Select one of the availabels paths by using the hash to select one of them
        Path path = getEcmpPath(ecmpCode, paths);
        return path;
    }

    /*
     * Given a set of Paths and a hash value,
     * it will use the value to select one of the paths and return it 
     */
    private Path getEcmpPath(long ecmpCode, Set<Path> paths) {
        long num_paths = paths.size();
        
        // Make sure the hash value is between 0 and num_paths exclusive
        int chosenPathIndex = Math.floorMod((int) ecmpCode, (int) num_paths);

        //log.info("num_paths: {}", num_paths);
        //log.info("Chosen Path index: {}", chosenPathIndex);

        Path path = (Path) paths.toArray()[chosenPathIndex];

        return path;
    }

    /*
    * Concatenates the 3 values and returns the hash of them.
    */
    private int ecmpCode(IpAddress srcIp, IpAddress dstIp, int flowLabel) {
        // Concatenate the values into a string representation
        String hashString = Objects.toString(srcIp) + Objects.toString(dstIp) + flowLabel;
        
        // Generate hash value from the concatenated string
        int hashValue = hashString.hashCode();
        
        return hashValue;
    }

    private Path pickForwardPathIfPossible(Set<Path> paths, PortNumber notToPort) {
        Path lastPath = null;
        for (Path path : paths) {
            lastPath = path;
            if (!path.src().port().equals(notToPort)) {
                return path;
            }
        }
        return lastPath;
    }

    private Set<Path> pickForwardPathSetIfPossible(Set<Path> paths, PortNumber notToPort) {
        Set<Path> pathSet = new HashSet<>();
        for (Path path : paths) {
            if (!path.src().port().equals(notToPort)) {
                pathSet.add(path);
            }
        }
        return pathSet;
    }

    /**
     * Packet processor responsible for forwarding packets along their paths.
     */
    /* I COMMENTED BECAUSE I DO NOT INTEED TO COMMENT PROCESS EACH PACKET INDIVIDUALLY
    private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.

            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            // Bail if this is deemed to be a control packet.
            if (isControlPacket(ethPkt)) {
                return;
            }
            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }
            IpAddress target = Ip4Address.valueOf(((IPv4) ethPkt.getPayload()).getDestinationAddress());
            IpAddress source = Ip4Address.valueOf(((IPv4) ethPkt.getPayload()).getSourceAddress());

            Set<Host> hosts =  hostService.getHostsByIp(target);
            if (null == hosts || hosts.size() == 0) {
                return;
            }

            // Skip IPv6 multicast packet when IPv6 forward is disabled.
            if (!ipv6Forwarding && isIpv6Multicast(ethPkt)) {
                return;
            }

            HostId id = HostId.hostId(ethPkt.getDestinationMAC());
            HostId srcId = HostId.hostId(ethPkt.getSourceMAC());
            // Do not process link-local addresses in any way.
            if (id.mac().isLinkLocal()) {
                return;
            }

            // Do not process IPv4 multicast packets, let mfwd handle them
            if (ignoreIpv4McastPackets && ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                if (id.mac().isMulticast()) {
                    return;
                }
            }

            // Do we know who this is for? If not, flood and bail.
            Host dst = hostService.getHost(id);
            Host src = hostService.getHost(srcId);
            if (dst == null) {
                flood(context);
                return;
            }

            // Are we on an edge switch that our destination is on? If so,
            // simply forward out to the destination and bail.
            if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                    installRule(context, dst.location().port());
                }
                return;
            }

            // Otherwise, get a set of paths that lead from here to the
            // destination edge switch.
            Set<Path> paths =
                    topologyService.getPaths(topologyService.currentTopology(),
                            pkt.receivedFrom().deviceId(),
                            dst.location().deviceId());
            if (paths.isEmpty()) {
                // If there are no paths, flood and bail.
                flood(context);
                return;
            }

            // Otherwise, pick a path that does not lead back to where we
            // came from; if no such path, flood and bail.
            Set<Path> pathSet = pickForwardPathSetIfPossible(paths, pkt.receivedFrom().port());
            if (pathSet.isEmpty()) {
                log.warn("Don't know where to go from here {} for {} -> {}",
                        pkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC());
                flood(context);
                return;
            }

            // Otherwise forward and be done with it.
            Path path = null;
            IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
            byte ipv4Protocol = ipv4Packet.getProtocol();
            if (ipv4Protocol == IPv4.PROTOCOL_TCP ) {
                TCP tcpPacket = (TCP) ipv4Packet.getPayload();
                long ecmpCode = ecmpCode(source, target, tcpPacket.getSourcePort(), tcpPacket.getDestinationPort());
                path = getEcmpPath(ecmpCode, paths);
            } else if (ipv4Protocol == IPv4.PROTOCOL_UDP) {
                UDP udpPacket = (UDP) ipv4Packet.getPayload();
                long ecmpCode = ecmpCode(source, target, udpPacket.getSourcePort(), udpPacket.getDestinationPort());
                path = getEcmpPath(ecmpCode, paths);
            } else {
                path = (Path) paths.toArray()[0];
            }
            //installRule(context, path.src().port());
            installRulesAlongPath(context, src.location(), dst.location(), path, ethPkt);
        }
    }*/





    private void installRulesAlongPath(PacketContext context, HostLocation srcHostLocation, HostLocation dstHostLocation, Path path, Ethernet inPkt) {
        List<Link>  linksSource = path.links();
        List<Link> links = Lists.newArrayList(linksSource);
        Collections.reverse(links);
        Link rightLink = null;
        IPv4 ipv4Packet = (IPv4) inPkt.getPayload();
        byte ipv4Protocol = ipv4Packet.getProtocol();
        Ip4Prefix matchIp4SrcPrefix =
                Ip4Prefix.valueOf(ipv4Packet.getSourceAddress(),
                        Ip4Prefix.MAX_MASK_LENGTH);
        Ip4Prefix matchIp4DstPrefix =
                Ip4Prefix.valueOf(ipv4Packet.getDestinationAddress(),
                        Ip4Prefix.MAX_MASK_LENGTH);
        TCP tcpPacket = null;
        UDP udpPacket = null;
        TpPort ipSourcePort = null;
        TpPort ipDestinationPort = null;
        if (ipv4Protocol == IPv4.PROTOCOL_TCP) {
            tcpPacket = (TCP) ipv4Packet.getPayload();
            ipSourcePort = TpPort.tpPort(tcpPacket.getSourcePort());
            ipDestinationPort = TpPort.tpPort(tcpPacket.getDestinationPort());
        } else if (ipv4Protocol == IPv4.PROTOCOL_UDP) {
            udpPacket = (UDP) ipv4Packet.getPayload();
            ipSourcePort = TpPort.tpPort(udpPacket.getSourcePort());
            ipDestinationPort = TpPort.tpPort(udpPacket.getDestinationPort());
        }

        for (Link link : links) {
            if (null != rightLink) {
                TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
                selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPSrc(matchIp4SrcPrefix)
                        .matchIPDst(matchIp4DstPrefix)
                        .matchIPProtocol(ipv4Protocol);
                if (ipv4Protocol == IPv4.PROTOCOL_TCP) {
                    selectorBuilder.matchTcpSrc(ipSourcePort)
                            .matchTcpDst(ipDestinationPort);
                } else if (ipv4Protocol == IPv4.PROTOCOL_UDP)  {
                    selectorBuilder.matchUdpSrc(ipSourcePort)
                            .matchUdpDst(ipDestinationPort);
                }
                // build selector
                selectorBuilder.matchInPort(link.dst().port());
                // build treatment
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setOutput(rightLink.src().port())
                        .build();
                ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                        .withSelector(selectorBuilder.build())
                        .withTreatment(treatment)
                        .withPriority(flowPriority)
                        .withFlag(ForwardingObjective.Flag.VERSATILE)
                        .fromApp(appId)
                        .makeTemporary(flowTimeout)
                        .add();
                flowObjectiveService.forward(link.dst().deviceId(), forwardingObjective);
            }
            if (link.src().equals(path.src())) {
                //    1.1 if the link is on the head, (srcHostLocation. link.src.port)
                TrafficSelector.Builder selectorBuilderHead = DefaultTrafficSelector.builder();
                selectorBuilderHead.matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPSrc(matchIp4SrcPrefix)
                        .matchIPDst(matchIp4DstPrefix)
                        .matchIPProtocol(ipv4Protocol)
                        .matchInPort(srcHostLocation.port());
                if (ipv4Protocol == IPv4.PROTOCOL_TCP) {
                    selectorBuilderHead.matchTcpSrc(ipSourcePort)
                            .matchTcpDst(ipDestinationPort);
                } else if (ipv4Protocol == IPv4.PROTOCOL_UDP){
                    selectorBuilderHead.matchUdpSrc(ipSourcePort)
                            .matchUdpDst(ipDestinationPort);
                }
                TrafficTreatment treatmentHead = DefaultTrafficTreatment.builder()
                        .setOutput(link.src().port())
                        .build();
                ForwardingObjective forwardingObjectiveHead = DefaultForwardingObjective.builder()
                        .withSelector(selectorBuilderHead.build())
                        .withTreatment(treatmentHead)
                        .withPriority(flowPriority)
                        .withFlag(ForwardingObjective.Flag.VERSATILE)
                        .fromApp(appId)
                        .makeTemporary(flowTimeout)
                        .add();
                flowObjectiveService.forward(link.src().deviceId(), forwardingObjectiveHead);
            }
            if (link.dst().equals(path.dst())) {
                TrafficSelector.Builder selectorBuilderTail = DefaultTrafficSelector.builder();
                selectorBuilderTail.matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPSrc(matchIp4SrcPrefix)
                        .matchIPDst(matchIp4DstPrefix)
                        .matchIPProtocol(ipv4Protocol)
                        .matchInPort(link.dst().port());
                // build treatment
                TrafficTreatment treatmentTail = DefaultTrafficTreatment.builder()
                        .setOutput(dstHostLocation.port())
                        .build();
                if (ipv4Protocol == IPv4.PROTOCOL_TCP) {
                    selectorBuilderTail.matchTcpSrc(ipSourcePort)
                            .matchTcpDst(ipDestinationPort);
                } else if (ipv4Protocol == IPv4.PROTOCOL_UDP){
                    selectorBuilderTail.matchUdpSrc(ipSourcePort)
                            .matchUdpDst(ipDestinationPort);
                }
                ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                        .withSelector(selectorBuilderTail.build())
                        .withTreatment(treatmentTail)
                        .withPriority(flowPriority)
                        .withFlag(ForwardingObjective.Flag.VERSATILE)
                        .fromApp(appId)
                        .makeTemporary(flowTimeout)
                        .add();
                flowObjectiveService.forward(link.dst().deviceId(), forwardingObjective);
            }
//            // 0. if the link is on the tail, (link.dst.port, dstHostLocation)
//            if (null == rightLink) {
//                // build selector
//                selectorBuilder.matchInPort(link.dst().port());
//                // build treatment
//                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
//                        .setOutput(dstHostLocation.port())
//                        .build();
//                ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
//                        .withSelector(selectorBuilder.build())
//                        .withTreatment(treatment)
//                        .withPriority(flowPriority)
//                        .withFlag(ForwardingObjective.Flag.VERSATILE)
//                        .fromApp(appId)
//                        .makeTemporary(flowTimeout)
//                        .add();
//                flowObjectiveService.forward(link.dst().deviceId(), forwardingObjective);
//                if (link.equals(path.src())) {
//                    //    1.1 if the link is on the head, (srcHostLocation. link.src.port)
//                    TrafficSelector.Builder selectorBuilderHead = DefaultTrafficSelector.builder();
//                    selectorBuilderHead.matchEthType(Ethernet.TYPE_IPV4)
//                            .matchIPSrc(matchIp4SrcPrefix)
//                            .matchIPDst(matchIp4DstPrefix)
//                            .matchInPort(srcHostLocation.port());
//                    if (ipv4Protocol == IPv4.PROTOCOL_TCP) {
//                        selectorBuilderHead.matchIPProtocol(ipv4Protocol)
//                                .matchTcpSrc(ipSourcePort)
//                                .matchTcpDst(ipDestinationPort);
//                    } else {
//                        selectorBuilderHead.matchIPProtocol(ipv4Protocol)
//                                .matchUdpSrc(ipSourcePort)
//                                .matchUdpDst(ipDestinationPort);
//                    }
//                    TrafficTreatment treatmentHead = DefaultTrafficTreatment.builder()
//                            .setOutput(link.src().port())
//                            .build();
//                    ForwardingObjective forwardingObjectiveHead = DefaultForwardingObjective.builder()
//                            .withSelector(selectorBuilderHead.build())
//                            .withTreatment(treatmentHead)
//                            .withPriority(flowPriority)
//                            .withFlag(ForwardingObjective.Flag.VERSATILE)
//                            .fromApp(appId)
//                            .makeTemporary(flowTimeout)
//                            .add();
//                    flowObjectiveService.forward(link.src().deviceId(), forwardingObjectiveHead);
//                }
//            }else {
//                // 1. install rule (link.dst.port. rightLink.src.port)
//                // build selector
//                selectorBuilder.matchInPort(link.dst().port());
//                // build treatment
//                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
//                        .setOutput(rightLink.src().port())
//                        .build();
//                ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
//                        .withSelector(selectorBuilder.build())
//                        .withTreatment(treatment)
//                        .withPriority(flowPriority)
//                        .withFlag(ForwardingObjective.Flag.VERSATILE)
//                        .fromApp(appId)
//                        .makeTemporary(flowTimeout)
//                        .add();
//                flowObjectiveService.forward(link.dst().deviceId(), forwardingObjective);
//                if (link.equals(path.src())) {
//                    //    1.1 if the link is on the head, (srcHostLocation. link.src.port)
//                    TrafficSelector.Builder selectorBuilderHead = DefaultTrafficSelector.builder();
//                    selectorBuilderHead.matchEthType(Ethernet.TYPE_IPV4)
//                            .matchIPSrc(matchIp4SrcPrefix)
//                            .matchIPDst(matchIp4DstPrefix)
//                            .matchInPort(srcHostLocation.port());
//                    if (ipv4Protocol == IPv4.PROTOCOL_TCP) {
//                        selectorBuilderHead.matchIPProtocol(ipv4Protocol)
//                                .matchTcpSrc(ipSourcePort)
//                                .matchTcpDst(ipDestinationPort);
//                    } else {
//                        selectorBuilderHead.matchIPProtocol(ipv4Protocol)
//                                .matchUdpSrc(ipSourcePort)
//                                .matchUdpDst(ipDestinationPort);
//                    }
//                    TrafficTreatment treatmentHead = DefaultTrafficTreatment.builder()
//                            .setOutput(link.src().port())
//                            .build();
//                    ForwardingObjective forwardingObjectiveHead = DefaultForwardingObjective.builder()
//                            .withSelector(selectorBuilderHead.build())
//                            .withTreatment(treatmentHead)
//                            .withPriority(flowPriority)
//                            .withFlag(ForwardingObjective.Flag.VERSATILE)
//                            .fromApp(appId)
//                            .makeTemporary(flowTimeout)
//                            .add();
//                    flowObjectiveService.forward(link.src().deviceId(), forwardingObjectiveHead);
//                }
//            }
            rightLink = link;
        }
        packetOut(dstHostLocation, inPkt);
    }

    private void packetOut(ConnectPoint connectPoint, Ethernet ethpkt) {
        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        builder.setOutput(connectPoint.port());
        packetService.emit(new DefaultOutboundPacket(connectPoint.deviceId(),
                builder.build(), ByteBuffer.wrap(ethpkt.serialize())));
        return;
    }

    // Indicates whether this is a control packet, e.g. LLDP, BDDP
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

    // Indicated whether this is an IPv6 multicast packet.
    private boolean isIpv6Multicast(Ethernet eth) {
        return eth.getEtherType() == Ethernet.TYPE_IPV6 && eth.isMulticast();
    }

    // Selects a path from the given set that does not lead back to the
    // specified port if possible.


    // Floods the specified packet if permissible.
    private void flood(PacketContext context) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();
        }
    }
    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    // Install a rule forwarding the packet to the specified port.
    private void installRule(PacketContext context, PortNumber portNumber) {
        //
        // We don't support (yet) buffer IDs in the Flow Service so
        // packet out first.
        //
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

        // If PacketOutOnly or ARP packet than forward directly to output port
        if (packetOutOnly || inPkt.getEtherType() == Ethernet.TYPE_ARP) {
            packetOut(context, portNumber);
            return;
        }

        //
        // If matchDstMacOnly
        //    Create flows matching dstMac only
        // Else
        //    Create flows with default matching and include configured fields
        //
        if (matchDstMacOnly) {
            selectorBuilder.matchEthDst(inPkt.getDestinationMAC());
        } else {
//
//            selectorBuilder.matchInPort(context.inPacket().receivedFrom().port())
//                    .matchEthSrc(inPkt.getSourceMAC())
//                    .matchEthDst(inPkt.getDestinationMAC());
            selectorBuilder.matchInPort(context.inPacket().receivedFrom().port());

            // If configured Match Vlan ID
            if (matchVlanId && inPkt.getVlanID() != Ethernet.VLAN_UNTAGGED) {
                selectorBuilder.matchVlanId(VlanId.vlanId(inPkt.getVlanID()));
            }

            //
            // If configured and EtherType is IPv4 - Match IPv4 and
            // TCP/UDP/ICMP fields
            //
            if (matchIpv4Address && inPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4Packet = (IPv4) inPkt.getPayload();
                byte ipv4Protocol = ipv4Packet.getProtocol();
                Ip4Prefix matchIp4SrcPrefix =
                        Ip4Prefix.valueOf(ipv4Packet.getSourceAddress(),
                                Ip4Prefix.MAX_MASK_LENGTH);
                Ip4Prefix matchIp4DstPrefix =
                        Ip4Prefix.valueOf(ipv4Packet.getDestinationAddress(),
                                Ip4Prefix.MAX_MASK_LENGTH);
                selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPSrc(matchIp4SrcPrefix)
                        .matchIPDst(matchIp4DstPrefix);

                if (matchIpv4Dscp) {
                    byte dscp = ipv4Packet.getDscp();
                    byte ecn = ipv4Packet.getEcn();
                    selectorBuilder.matchIPDscp(dscp).matchIPEcn(ecn);
                }

                if (matchTcpUdpPorts && ipv4Protocol == IPv4.PROTOCOL_TCP) {
                    TCP tcpPacket = (TCP) ipv4Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv4Protocol)
                            .matchTcpSrc(TpPort.tpPort(tcpPacket.getSourcePort()))
                            .matchTcpDst(TpPort.tpPort(tcpPacket.getDestinationPort()));
                }
                if (matchTcpUdpPorts && ipv4Protocol == IPv4.PROTOCOL_UDP) {
                    UDP udpPacket = (UDP) ipv4Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv4Protocol)
                            .matchUdpSrc(TpPort.tpPort(udpPacket.getSourcePort()))
                            .matchUdpDst(TpPort.tpPort(udpPacket.getDestinationPort()));
                }
                if (matchIcmpFields && ipv4Protocol == IPv4.PROTOCOL_ICMP) {
                    ICMP icmpPacket = (ICMP) ipv4Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv4Protocol)
                            .matchIcmpType(icmpPacket.getIcmpType())
                            .matchIcmpCode(icmpPacket.getIcmpCode());
                }
            }

            //
            // If configured and EtherType is IPv6 - Match IPv6 and
            // TCP/UDP/ICMP fields
            //
            if (matchIpv6Address && inPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                IPv6 ipv6Packet = (IPv6) inPkt.getPayload();
                byte ipv6NextHeader = ipv6Packet.getNextHeader();
                Ip6Prefix matchIp6SrcPrefix =
                        Ip6Prefix.valueOf(ipv6Packet.getSourceAddress(),
                                Ip6Prefix.MAX_MASK_LENGTH);
                Ip6Prefix matchIp6DstPrefix =
                        Ip6Prefix.valueOf(ipv6Packet.getDestinationAddress(),
                                Ip6Prefix.MAX_MASK_LENGTH);
                selectorBuilder.matchEthType(Ethernet.TYPE_IPV6)
                        .matchIPv6Src(matchIp6SrcPrefix)
                        .matchIPv6Dst(matchIp6DstPrefix);

                if (matchIpv6FlowLabel) {
                    selectorBuilder.matchIPv6FlowLabel(ipv6Packet.getFlowLabel());
                }

                if (matchTcpUdpPorts && ipv6NextHeader == IPv6.PROTOCOL_TCP) {
                    TCP tcpPacket = (TCP) ipv6Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv6NextHeader)
                            .matchTcpSrc(TpPort.tpPort(tcpPacket.getSourcePort()))
                            .matchTcpDst(TpPort.tpPort(tcpPacket.getDestinationPort()));
                }
                if (matchTcpUdpPorts && ipv6NextHeader == IPv6.PROTOCOL_UDP) {
                    UDP udpPacket = (UDP) ipv6Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv6NextHeader)
                            .matchUdpSrc(TpPort.tpPort(udpPacket.getSourcePort()))
                            .matchUdpDst(TpPort.tpPort(udpPacket.getDestinationPort()));
                }
                if (matchIcmpFields && ipv6NextHeader == IPv6.PROTOCOL_ICMP6) {
                    ICMP6 icmp6Packet = (ICMP6) ipv6Packet.getPayload();
                    selectorBuilder.matchIPProtocol(ipv6NextHeader)
                            .matchIcmpv6Type(icmp6Packet.getIcmpType())
                            .matchIcmpv6Code(icmp6Packet.getIcmpCode());
                }
            }
        }
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(portNumber)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(flowTimeout)
                .add();

        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(),
                forwardingObjective);

        //
        // If packetOutOfppTable
        //  Send packet back to the OpenFlow pipeline to match installed flow
        // Else
        //  Send packet direction on the appropriate port
        //
        if (packetOutOfppTable) {
            packetOut(context, PortNumber.TABLE);
        } else {
            packetOut(context, portNumber);
        }
    }

    private class InternalTopologyListener implements TopologyListener {
        @Override
        public void event(TopologyEvent event) {
            List<Event> reasons = event.reasons();
            if (reasons != null) {
                reasons.forEach(re -> {
                    if (re instanceof LinkEvent) {
                        LinkEvent le = (LinkEvent) re;
                        if (le.type() == LinkEvent.Type.LINK_REMOVED) {
                            fixBlackhole(le.subject().src());
                        }
                    }
                });
            }
        }
    }

    private void fixBlackhole(ConnectPoint egress) {
        Set<FlowEntry> rules = getFlowRulesFrom(egress);
        Set<SrcDstPair> pairs = findSrcDstPairs(rules);

        Map<DeviceId, Set<Path>> srcPaths = new HashMap<>();

        for (SrcDstPair sd : pairs) {
            // get the edge deviceID for the src host
            Host srcHost = hostService.getHost(HostId.hostId(sd.src));
            Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
            if (srcHost != null && dstHost != null) {
                DeviceId srcId = srcHost.location().deviceId();
                DeviceId dstId = dstHost.location().deviceId();
                log.trace("SRC ID is " + srcId + ", DST ID is " + dstId);

                cleanFlowRules(sd, egress.deviceId());

                Set<Path> shortestPaths = srcPaths.get(srcId);
                if (shortestPaths == null) {
                    shortestPaths = topologyService.getPaths(topologyService.currentTopology(),
                            egress.deviceId(), srcId);
                    srcPaths.put(srcId, shortestPaths);
                }
                backTrackBadNodes(shortestPaths, dstId, sd);
            }
        }
    }

    // Backtracks from link down event to remove flows that lead to blackhole
    private void backTrackBadNodes(Set<Path> shortestPaths, DeviceId dstId, SrcDstPair sd) {
        for (Path p : shortestPaths) {
            List<Link> pathLinks = p.links();
            for (int i = 0; i < pathLinks.size(); i = i + 1) {
                Link curLink = pathLinks.get(i);
                DeviceId curDevice = curLink.src().deviceId();

                // skipping the first link because this link's src has already been pruned beforehand
                if (i != 0) {
                    cleanFlowRules(sd, curDevice);
                }

                Set<Path> pathsFromCurDevice =
                        topologyService.getPaths(topologyService.currentTopology(),
                                curDevice, dstId);
                if (pickForwardPathIfPossible(pathsFromCurDevice, curLink.src().port()) != null) {
                    break;
                } else {
                    if (i + 1 == pathLinks.size()) {
                        cleanFlowRules(sd, curLink.dst().deviceId());
                    }
                }
            }
        }
    }

    // Removes flow rules off specified device with specific SrcDstPair
    private void cleanFlowRules(SrcDstPair pair, DeviceId id) {
        log.trace("Searching for flow rules to remove from: " + id);
        log.trace("Removing flows w/ SRC=" + pair.src + ", DST=" + pair.dst);
        for (FlowEntry r : flowRuleService.getFlowEntries(id)) {
            boolean matchesSrc = false, matchesDst = false;
            for (Instruction i : r.treatment().allInstructions()) {
                if (i.type() == Instruction.Type.OUTPUT) {
                    // if the flow has matching src and dst
                    for (Criterion cr : r.selector().criteria()) {
                        if (cr.type() == Criterion.Type.ETH_DST) {
                            if (((EthCriterion) cr).mac().equals(pair.dst)) {
                                matchesDst = true;
                            }
                        } else if (cr.type() == Criterion.Type.ETH_SRC) {
                            if (((EthCriterion) cr).mac().equals(pair.src)) {
                                matchesSrc = true;
                            }
                        }
                    }
                }
            }
            if (matchesDst && matchesSrc) {
                log.trace("Removed flow rule from device: " + id);
                flowRuleService.removeFlowRules((FlowRule) r);
            }
        }

    }

    // Returns a set of src/dst MAC pairs extracted from the specified set of flow entries
    private Set<SrcDstPair> findSrcDstPairs(Set<FlowEntry> rules) {
        ImmutableSet.Builder<SrcDstPair> builder = ImmutableSet.builder();
        for (FlowEntry r : rules) {
            MacAddress src = null, dst = null;
            for (Criterion cr : r.selector().criteria()) {
                if (cr.type() == Criterion.Type.ETH_DST) {
                    dst = ((EthCriterion) cr).mac();
                } else if (cr.type() == Criterion.Type.ETH_SRC) {
                    src = ((EthCriterion) cr).mac();
                }
            }
            builder.add(new SrcDstPair(src, dst));
        }
        return builder.build();
    }

    // Returns set of flow entries which were created by this application and
    // which egress from the specified connection port
    private Set<FlowEntry> getFlowRulesFrom(ConnectPoint egress) {
        ImmutableSet.Builder<FlowEntry> builder = ImmutableSet.builder();
        flowRuleService.getFlowEntries(egress.deviceId()).forEach(r -> {
            if (r.appId() == appId.id()) {
                r.treatment().allInstructions().forEach(i -> {
                    if (i.type() == Instruction.Type.OUTPUT) {
                        if (((Instructions.OutputInstruction) i).port().equals(egress.port())) {
                            builder.add(r);
                        }
                    }
                });
            }
        });

        return builder.build();
    }

    // Wrapper class for a source and destination pair of MAC addresses
    private final class SrcDstPair {
        final MacAddress src;
        final MacAddress dst;

        private SrcDstPair(MacAddress src, MacAddress dst) {
            this.src = src;
            this.dst = dst;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SrcDstPair that = (SrcDstPair) o;
            return Objects.equals(src, that.src) &&
                    Objects.equals(dst, that.dst);
        }

        @Override
        public int hashCode() {
            return Objects.hash(src, dst);
        }
    }

    /**
     * Returns the Srv6 config object for the given device.
     *
     * @param deviceId the device ID
     * @return Srv6  device config
     */
    private Optional<Srv6DeviceConfig> getDeviceConfig(DeviceId deviceId) {
        Srv6DeviceConfig config = networkConfigService.getConfig(
                deviceId, Srv6DeviceConfig.class);
        return Optional.ofNullable(config);
    }
    
    /**
     * Returns the IP address configured in the "subNetIP" property of the
     * given device config.
     *
     * @param deviceId the device ID
     * @return MyStation MAC address
     */
    private Ip6Address getMySubNetIP(DeviceId deviceId) {
        return getDeviceConfig(deviceId)
                .map(Srv6DeviceConfig::mySubNetIP)
                .orElseThrow(() -> new ItemNotFoundException(
                        "Missing mySubNetIP config for " + deviceId));
    }
}
