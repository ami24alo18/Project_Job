package com.amit.jobagent.jobsource.discovery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@FunctionalInterface
interface CareerSiteDnsResolver {
    List<InetAddress> resolve(String host) throws UnknownHostException;
}
