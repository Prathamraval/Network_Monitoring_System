package org.nms.utils;

import java.net.InetSocketAddress;
import java.net.Socket;

public class DeviceReachability
{

    public static void performPingCheck(String ipAddress) throws Exception
    {
        var pb = new ProcessBuilder("fping", "-c1", "-t1000", ipAddress); // -c1 = 1 ping, -t = 1s timeout

        pb.redirectErrorStream(true);

        var process = pb.start();

        var exitCode = process.waitFor();

        if (exitCode != 0)
        {
            throw new Exception("fping check failed: Device is not reachable");
        }

    }

    public static void performPortCheck(String ipAddress, Integer portNo) throws Exception
    {
        var socket = new Socket();

        socket.connect(new InetSocketAddress(ipAddress, portNo), 1000); // 1s timeout

        socket.close();
    }

}
