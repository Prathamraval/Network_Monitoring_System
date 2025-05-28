package org.nms.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Logger;

public class DeviceReachability
{
    public static boolean performPingCheck(String ipAddress)
    {
        try
        {
            // Use a simple ping command: ping <ipAddress>
            ProcessBuilder pb = new ProcessBuilder("ping", "-c", "1", "-W", "2", ipAddress);
            pb.redirectErrorStream(true);

            var process = pb.start();
            var exitCode = process.waitFor();

            if (exitCode == 0)
            {
                return true;
            }
            else
            {
                return false;
            }
        }
        catch (Exception exception)
        {
            return false;
        }
    }

    public static boolean performPortCheck(String ipAddress, Integer portNo)
    {
        try
        {
            var socket = new Socket();
            socket.connect(new InetSocketAddress(ipAddress, portNo), 1000); // 1s timeout
            socket.close();
            return true;
        }
        catch (IOException exception)
        {
            return false;
        }
    }
}
