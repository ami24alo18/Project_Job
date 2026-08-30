package com.amit.jobagent.jobsource.discovery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class SystemCareerSiteDnsResolver implements CareerSiteDnsResolver {
    @Override
    public List<InetAddress> resolve(String host) throws UnknownHostException {
        return Arrays.asList(InetAddress.getAllByName(host));
    }
}
