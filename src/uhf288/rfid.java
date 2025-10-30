package uhf288;

import com.rfid.uhf288.Device;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class rfid {

    /**
     * Set reader frequency region
     * @param reader Reader object
     * @param comAddr Communication address
     * @param band Frequency band configuration
     * @param minFreqPoint Minimum frequency point (0-63)
     * @param maxFreqPoint Maximum frequency point (0-63)
     * @param PortHandle Port handle
     * @return Operation result
     */
    private static int setFrequencyRegion(com.rfid.uhf288.Device reader, byte[] comAddr, byte band, byte minFreqPoint, byte maxFreqPoint, int PortHandle) {
        // Configure frequency band bits (bit7 and bit6)
        byte dmaxfre = (byte) ((band << 6) | (maxFreqPoint & 0x3F));
        byte dminfre = (byte) ((band << 6) | (minFreqPoint & 0x3F));

        return reader.SetRegion(comAddr, dmaxfre, dminfre, PortHandle);
    }

    
    /**
     * 辅助函数：将十六进制字符串转换为字节数组
     * @param s 十六进制字符串
     * @return 对应的字节数组
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
  

    // 定义一个内部类来存储标签的详细信息
    static class TagInfo {
        int readCount = 0;
        int rssi = 0;
        double initialPhaseDegrees = 0;
        double finalPhaseDegrees = 0;
        double frequencyMHz = 0;
        String userData = "N/A"; // 用户数据，默认为N/A

        public TagInfo() {}
    }


    /**
     * Main function - RFID reader test program
     * @param args Command line arguments
     */
    public static void main(String[] args) {

        // --- START: CODE FOR FILE REDIRECTION ---
        // 1. 检查是否在命令行中提供了文件名参数
        if (args.length == 0) {
            System.err.println("错误: 未指定输出文件名。");
            System.err.println("用法: java uhf288.rfid <filename.txt>");
            return; // 如果没有提供文件名，则退出程序
        }
        String outputFileName = args[0];

        // 2. 尝试将 System.out 重定向到指定文件
        try {
            // 创建一个新的 PrintStream 用于写入文件
            PrintStream fileOut = new PrintStream(new FileOutputStream(outputFileName));
            
            // 重定向标准输出流
            System.setOut(fileOut);
            
            // (可选) 同时也将标准错误流重定向到同一个文件
            System.setErr(fileOut);

        } catch (IOException e) {
            System.err.println("错误: 无法写入文件 " + outputFileName);
            e.printStackTrace();
            return; // 如果文件无法打开，则退出程序
        }
        // --- END: CODE FOR FILE REDIRECTION ---


        // 1. Serial port communication initialization
        System.loadLibrary("com_rfid_uhf288_Device");
        com.rfid.uhf288.Device reader = new com.rfid.uhf288.Device();

        int Port = 5; // COM5
        byte[] comAddr = new byte[1];
        comAddr[0] = (byte) 255;
        byte baud = 5; // 57600bps
        int[] PortHandle = new int[1];

        int result = reader.OpenComPort(Port, comAddr, baud, PortHandle);
        System.out.println("Open serial port: " + result);

        if (result != 0) {
            System.out.println("Failed to open serial port, program exiting");
            return;
        }

        // 2. Reader information acquisition
        byte[] versionInfo = new byte[2];
        byte[] readerType = new byte[1];
        byte[] trType = new byte[1];
        byte[] dmaxfre = new byte[1];
        byte[] dminfre = new byte[1];
        byte[] powerdBm = new byte[1];
        byte[] InventoryScanTime = new byte[1];
        byte[] Ant = new byte[1];
        byte[] BeepEn = new byte[1];
        byte[] OutputRep = new byte[1];
        byte[] CheckAnt = new byte[1];

        result = reader.GetReaderInformation(comAddr, versionInfo, readerType, trType, dmaxfre, dminfre,
                                            powerdBm, InventoryScanTime, Ant, BeepEn, OutputRep, CheckAnt, PortHandle[0]);
        System.out.println("Get reader information: " + result);
        if (result == 0) {
            System.out.println("Firmware Version: V" + (versionInfo[0] & 0xFF) + "." + (versionInfo[1] & 0xFF));
            System.out.println("Antenna " + (Ant[0] & 0xFF));
            System.out.println("Antenna Connection Status: " + (CheckAnt[0] & 0xFF));
        } else {
            System.out.println("Failed to get reader information, program exiting");
            reader.CloseSpecComPort(PortHandle[0]);
            return;
        }

        // byte opt = 1;//设置并保存
        byte[] profileToSet = new byte[1];
        profileToSet[0] =(byte) 0xCD; // Profile 1
        result = reader.SetProfile(comAddr, profileToSet, PortHandle[0]);
        if (result != 0) {
            System.out.println("Failed to set profile, error code: " + result);
            reader.CloseSpecComPort(PortHandle[0]);
            return;
        } else {
            System.out.println("Profile set successfully.");
        }   

        
        // 定义要读取的特定EPC
        String targetEpcString = "E2806894000050315997199E";
        
        // --- 配置 Inventory_G2 的通用参数 ---
        byte QValue = (byte) 0x12; // Q=2, 返回相位
        byte Session = (byte) 0x00; // S0
        byte Target = 0;
        byte InAnt = (byte)0x01; // 天线1
        byte Scantime = 1;       // 短扫描时间
        byte FastFlag = 0;
        
        // --- 配置 Inventory_G2 的Mask参数，用于筛选特定EPC ---
        byte MaskMem = 1;                     // 1 = EPC内存区
        byte[] MaskAdr = new byte[2];
        MaskAdr[0] = 0x00;
        MaskAdr[1] = 0x20;                    // 起始地址 32 bits (跳过CRC和PC)
        byte MaskLen = (byte) (targetEpcString.length() * 4); // EPC长度 (96 bits)
        byte[] MaskData = new byte[256];
        
        // 将EPC字符串转换为字节数组，并复制到MaskData中
        byte[] targetEpcBytes = hexStringToByteArray(targetEpcString);
        System.arraycopy(targetEpcBytes, 0, MaskData, 0, targetEpcBytes.length);
        
        byte MaskFlag = 1;                    // 1 = 启用Mask功能
        
        // --- TID参数 (如果不需要读取TID，可以禁用) ---
        byte AdrTID = 0;
        byte LenTID = 6;
        byte TIDFlag = 1; // 1 = 读取TID, 0 = 不读取TID
        
        byte[] pEPCList = new byte[20000];
        int[] Totallen = new int[1];
        int[] CardNum = new int[1];

        // 设置天线复用
        result = reader.SetAntennaMultiplexing(comAddr, InAnt, PortHandle[0]);
        System.out.println("Set antenna multiplexing: " + result);

        final int NUM_QUERIES_PER_FREQ = 20; // 每个频点发送的查询命令次数

        for (int freqPoint = 0; freqPoint <=49; freqPoint++) {
          
            byte dmaxfre_single = (byte) (0x00 | (freqPoint & 0x3F)); // US band
            byte dminfre_single = (byte) (0x80 | (freqPoint & 0x3F)); // US band  
            result = reader.SetRegion(comAddr, dmaxfre_single, dminfre_single, PortHandle[0]);
            if (result != 0) {
                System.out.println("Failed to set frequency point " + freqPoint + ": " + result);
                continue;
            }
            
            // 用于存储当前频点下所有标签的信息
            Map<String, TagInfo> tagDataMap = new HashMap<>();

            for (int queryCount = 0; queryCount < NUM_QUERIES_PER_FREQ; queryCount++) {
                
                result = reader.Inventory_G2(comAddr, QValue, Session, MaskMem, MaskAdr, MaskLen, MaskData, MaskFlag,
                                            AdrTID, LenTID, TIDFlag, Target, InAnt, Scantime, FastFlag, pEPCList,
                                            Ant, Totallen, CardNum, PortHandle[0]);
                System.out.println("Inventory_G2 result: 0x" + Integer.toHexString(result & 0xFFFF).toUpperCase());
                
                if (CardNum[0] > 0) {
                    Set<String> uniqueEPCsThisQuery = new HashSet<>();
                    int m = 0;
                    for (int index = 0; index < CardNum[0]; index++) {
                        // --- START: 暂存盘存数据 ---
                        // 为了应对ReadData失败后需要丢弃数据的情况，我们先解析并暂存
                        int current_m = m; // 记录当前指针位置
                        int len_byte = pEPCList[m++] & 0xFF;
                        int epclen = len_byte & 0x3F;
                        String EPCstr = "";
                        byte[] epc = new byte[epclen];
                        for (int n = 0; n < epclen; n++) {
                            byte bbt = pEPCList[m++];
                            epc[n] = bbt;
                            String hex = Integer.toHexString(bbt & 255);
                            if (hex.length() == 1) {
                                hex = "0" + hex;
                            }
                            EPCstr += hex;
                        }
                        EPCstr = EPCstr.toUpperCase();
                        
                        // 暂存RSSI和相位等信息
                        int temp_rssi = pEPCList[m++] & 255;
                        int temp_initialPhaseRaw = ((pEPCList[m++] & 0xFF) << 8) | (pEPCList[m++] & 0xFF);
                        int temp_finalPhaseRaw = ((pEPCList[m++] & 0xFF) << 8) | (pEPCList[m++] & 0xFF);
                        int temp_currentTagFreq = ((pEPCList[m++] & 0xFF) << 16) | ((pEPCList[m++] & 0xFF) << 8) | (pEPCList[m++] & 0xFF);
                        // --- END: 暂存盘存数据 ---

                        
                        // --- 尝试读取用户数据 ---
                        byte ENum = (byte) 255;
                        byte Mem = 1; // USER 存储区
                        byte WordPtr = 2;
                        byte Num = 6;
                        byte[] Password = new byte[4];
                        byte ReadMaskMem = 2; // 使用EPC进行掩码
                        byte[] ReadMaskAdr = {0, 0};
                        byte ReadMaskLen = (byte) (epclen * 8);
                        byte[] ReadMaskData = new byte[epclen];
                        System.arraycopy(epc, 0, ReadMaskData, 0, epclen);
                        byte[] Data = new byte[Num * 2];
                        int[] Errorcode = new int[1];

                        int readResult = reader.ReadData_G2(comAddr, epc, ENum, Mem, WordPtr, Num, Password,
                                                            ReadMaskMem, ReadMaskAdr, ReadMaskLen, ReadMaskData, Data, Errorcode, PortHandle[0]);
                        System.out.println("ReadData_G2 result for " + EPCstr + ": 0x" + Integer.toHexString(readResult & 0xFFFF).toUpperCase());

                        // =================================================================
                        // =============== START: 核心逻辑修改 ==============================
                        // === 只有在 ReadData_G2 成功时 (返回0)，才更新所有信息 =========
                        // =================================================================
                        if (readResult == 0) {
                            // 获取或创建TagInfo对象
                            TagInfo currentTagInfo = tagDataMap.getOrDefault(EPCstr, new TagInfo());

                            // 检查是否是本次查询中第一次遇到该EPC，如果是，则增加读取计数
                            if (!uniqueEPCsThisQuery.contains(EPCstr)) {
                                uniqueEPCsThisQuery.add(EPCstr);
                                currentTagInfo.readCount++;
                            }
                            
                            // 更新RSSI和相位等从盘存中获取的信息
                            currentTagInfo.rssi = temp_rssi;
                            final double PHASE_UNIT = 0.087;
                            currentTagInfo.initialPhaseDegrees = temp_initialPhaseRaw * PHASE_UNIT;
                            currentTagInfo.finalPhaseDegrees = temp_finalPhaseRaw * PHASE_UNIT;
                            currentTagInfo.frequencyMHz = temp_currentTagFreq / 1000.0;

                            // 解析并更新用户数据
                            String Memdata = "";
                            for (int p = 0; p < Num * 2; p++) {
                                byte bbt = Data[p];
                                String hex = Integer.toHexString(bbt & 255);
                                if (hex.length() == 1) {
                                    hex = "0" + hex;
                                }
                                Memdata += hex;
                            }
                            currentTagInfo.userData = Memdata.toUpperCase();
                            
                            // 将更新后的信息存回Map
                            tagDataMap.put(EPCstr, currentTagInfo);
                        }
                        // 如果 readResult != 0, 则什么都不做，直接忽略这次盘存结果
                        // =================================================================
                        // =============== END: 核心逻辑修改 ================================
                        // =================================================================
                    }
                }
            }

            // 在当前频点所有查询结束后，打印统计结果
            if (tagDataMap.isEmpty()) {
                System.out.println("No tags with successfully read user data detected after " + NUM_QUERIES_PER_FREQ + " queries.");
            } else {
                for (Map.Entry<String, TagInfo> entry : tagDataMap.entrySet()) {
                    String epc = entry.getKey();
                    TagInfo info = entry.getValue();

                    double successRate = (double) info.readCount / NUM_QUERIES_PER_FREQ;
                    double packetLossRate = 1.0 - successRate;

                    System.out.println("  Tag EPC: " + epc);
                    System.out.println("    Successful Reads (Inv+ReadUser): " + info.readCount + "/" + NUM_QUERIES_PER_FREQ);
                    System.out.printf("    Packet Loss Rate: %.2f%%\n", packetLossRate * 100);
                    System.out.println("    RSSI: " + info.rssi + " dBm");
                    System.out.printf("    Initial Phase: %.2f°\n", info.initialPhaseDegrees);
                    System.out.printf("    Final Phase: %.2f°\n", info.finalPhaseDegrees);
                    System.out.printf("    Reported Frequency: %.3f MHz\n", info.frequencyMHz);
                    System.out.println("    User Data: " + info.userData);
                    System.out.println("------------------------------------");
                }
            }
        }

        // Close serial port
        reader.CloseSpecComPort(PortHandle[0]);
        System.out.println("\nProgram execution completed, serial port closed");
    }
}