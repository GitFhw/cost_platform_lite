package com.ruoyi.system.service.cost.remote;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Guards server-side remote calls from reaching local or private network targets.
 */
@Service
public class RemoteEndpointGuard {
    private static final String CLOUD_METADATA_IP = "169.254.169.254";

    public void validate(URI uri) {
        if (uri == null) {
            throw new ServiceException("第三方接口地址不能为空");
        }
        String scheme = StringUtils.defaultString(uri.getScheme()).toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new ServiceException("第三方接口地址仅允许 http 或 https");
        }
        String host = StringUtils.trimToEmpty(uri.getHost()).toLowerCase(Locale.ROOT);
        if (StringUtils.isEmpty(host)) {
            throw new ServiceException("第三方接口地址必须包含有效域名或IP");
        }
        if (isBlockedHostName(host)) {
            throw new ServiceException("第三方接口地址不能访问本机或内网保留地址");
        }
        validateResolvedAddresses(host);
    }

    private boolean isBlockedHostName(String host) {
        return "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || CLOUD_METADATA_IP.equals(host)
                || isBlockedIpLiteral(host);
    }

    private boolean isBlockedIpLiteral(String host) {
        try {
            if (!host.matches("^[0-9a-fA-F:.]+$")) {
                return false;
            }
            return isBlockedAddress(InetAddress.getByName(host));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void validateResolvedAddresses(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    throw new ServiceException("第三方接口地址解析到本机或内网保留地址，已阻断");
                }
            }
        } catch (UnknownHostException ignored) {
            // The actual HTTP client will surface unreachable hosts. Keeping unresolved
            // public-style domains allowed preserves mock tests and offline configuration drafts.
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address == null) {
            return true;
        }
        String hostAddress = address.getHostAddress();
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || CLOUD_METADATA_IP.equals(hostAddress)
                || isUniqueLocalIpv6(hostAddress);
    }

    private boolean isUniqueLocalIpv6(String hostAddress) {
        String normalized = StringUtils.defaultString(hostAddress).toLowerCase(Locale.ROOT);
        return normalized.startsWith("fc") || normalized.startsWith("fd");
    }
}
