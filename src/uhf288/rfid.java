package uhf288;

import java.lang.reflect.Array;
import com.rfid.uhf288.Device.*;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class rfid {

    // ... (其他方法和常量保持不变)

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
     * Calculate frequency value for Chinese band 1
     * @param freqPoint Frequency point (0-19)
     * @return Frequency in Hz
     */
    private static int calculateChineseBand1Frequency(int freqPoint) {
        // Chinese band 1: Fs = 840.125 + N * 0.25 (MHz) where N∈ [0, 19]
        return 840125 + freqPoint * 250; // 840.125MHz + N * 0.25MHz
    }

    /**
     * Calculate frequency value for Chinese band 2
     * @param freqPoint Frequency point (0-19)
     * @return Frequency in Hz
     */
    private static int calculateChineseBand2Frequency(int freqPoint) {
        // Chinese band 2: Fs = 920.125 + N * 0.25 (MHz) where N∈ [0, 19]
        return 920125 + freqPoint * 250; // 920.125MHz + N * 0.25MHz
    }


    /**
     * Frequency band constants
     */
    private static final byte BAND_CHINESE1 = (byte)0x80; // 10000000 in binary (bit7=1, bit6=0)
    private static final byte BAND_CHINESE2 =(byte)0x00; // 00000000 in binary (bit7=0, bit6=0)


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
        // ... (原有的初始化代码保持不变)

        // 1. Serial port communication initialization
        System.loadLibrary("com_rfid_uhf288_Device");
        com.rfid.uhf288.Device reader = new com.rfid.uhf288.Device();

        int Port = 5; // COM1
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

        // 3. 执行标签读取操作和数据统计

        // EPC C1-G2 standard tag reading parameters
        byte QValue = (byte) 0x12; //0001 0010 -> Q=2 可读取标签为2^Q个
        byte Session = (byte) 0x00; //确保每次盘存都从SL=0开始
        byte MaskMem =0;
        byte[] MaskAdr = new byte[2];
        byte MaskLen = 0;
        byte[] MaskData = new byte[256];
        byte MaskFlag = 0;
        byte AdrTID = 0;
        byte LenTID = 6;
        byte TIDFlag = 1;
        byte Target = 0;
        byte InAnt = (byte)0x01; // Assuming antenna 1
        byte Scantime = 2; // 较短的盘存时间
        byte FastFlag = 0;
        byte[] pEPCList = new byte[20000];
        int[] Totallen = new int[1];
        int[] CardNum = new int[1];

        // 设置天线复用
        result = reader.SetAntennaMultiplexing(comAddr, InAnt, PortHandle[0]);
        System.out.println("Set antenna multiplexing: " + result);

        final int NUM_QUERIES_PER_FREQ = 5; // 每个频点发送的查询命令次数

        // 循环测试两个频段 (0: Chinese Band 1, 1: Chinese Band 2)
        for (int bandIndex = 0; bandIndex < 2; bandIndex++) {
            // String bandName = (bandIndex == 0) ? "Chinese Band 1 (840MHz range)" : "Chinese Band 2 (920MHz range)";
            // byte currentBand = (bandIndex == 0) ? BAND_CHINESE1 : BAND_CHINESE2;

            // System.out.println("\n=== Testing " + bandName + " - Individual Frequency Points ===");

            for (int freqPoint = 0; freqPoint <= 19; freqPoint++) {
                byte dmaxfre_single = 0;
                byte dminfre_single = 0;
                if(bandIndex==0){
                dmaxfre_single = (byte) (0x80 | (freqPoint & 0x3F));
                dminfre_single = (byte) (0x00 | (freqPoint & 0x3F)); 
                }else{
                dmaxfre_single = (byte) (0x00 | (freqPoint & 0x3F)); 
                dminfre_single  = (byte) (0x40 | (freqPoint & 0x3F)); 
                }
                
                result = reader.SetRegion(comAddr, dmaxfre_single, dminfre_single, PortHandle[0]);
                if (result != 0) {
                    System.out.println("Failed to set frequency point " + freqPoint + ": " + result);
                    continue;
                }
                
                // System.out.println("Current antenna configuration (Ant): " + (InAnt & 0xFF));
                // System.out.println("Current Tx Power (powerdBm): " + powerdBm[0] + " dBm");
                // System.out.println("Current Inventory Scan Time (InventoryScanTime): " + InventoryScanTime[0]);

                // 用于存储当前频点下所有标签的信息
                Map<String, TagInfo> tagDataMap = new HashMap<>();

                for (int queryCount = 0; queryCount < NUM_QUERIES_PER_FREQ; queryCount++) {
                    // 执行盘存
                    result = reader.Inventory_G2(comAddr, QValue, Session, MaskMem, MaskAdr, MaskLen, MaskData, MaskFlag,
                                                AdrTID, LenTID, TIDFlag, Target, InAnt, Scantime, FastFlag, pEPCList,
                                                Ant, Totallen, CardNum, PortHandle[0]);

                    // if (result == 0) { // 即使没有标签，也可能返回0
                    //     System.out.println("Tag inventory result (query " + (queryCount + 1) + "): " + result + ", CardNum: " + CardNum[0]);
                    // } else {
                    //     System.out.println("Tag inventory result (query " + (queryCount + 1) + "): " + result);
                    // }

                    if (CardNum[0] > 0) {
                        // System.out.println("Tags detected in this query: " + CardNum[0]);
                        Set<String> uniqueEPCsThisQuery = new HashSet<>();
                        // System.out.println("  Query " + (queryCount + 1) + ": Detected " + CardNum[0] + " tag(s)");
                        int m = 0;
                        for (int index = 0; index < CardNum[0]; index++) {
                            // 解析EPC数据
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
                            if (!uniqueEPCsThisQuery.contains(EPCstr)) {
                                uniqueEPCsThisQuery.add(EPCstr); // 标记为已处理

                                // 获取或创建TagInfo对象
                                TagInfo currentTagInfo = tagDataMap.getOrDefault(EPCstr, new TagInfo());
                                currentTagInfo.readCount++; // 只有在本次查询中第一次遇到该EPC时才增加读取次数
                                tagDataMap.put(EPCstr, currentTagInfo); // 更新Map中的数据 (即使没有修改也确保存在)
                            }

                            // 获取或创建TagInfo对象
                            TagInfo currentTagInfo = tagDataMap.getOrDefault(EPCstr, new TagInfo());
                            // currentTagInfo.readCount++; // 增加读取次数
                            // System.out.println("  Detected Tag EPC: " + EPCstr + " (Total Reads: " + currentTagInfo.readCount + ")");

                            // 更新RSSI和相位（只保留最后一次查询结果）
                            currentTagInfo.rssi = pEPCList[m++] & 255; // RSSI
                            final double PHASE_UNIT = 0.087;
                            int initialPhaseRaw = ((pEPCList[m++] & 0xFF) << 8) | (pEPCList[m++] & 0xFF);
                            int finalPhaseRaw = ((pEPCList[m++] & 0xFF) << 8) | (pEPCList[m++] & 0xFF);
                            currentTagInfo.initialPhaseDegrees = initialPhaseRaw * PHASE_UNIT;
                            currentTagInfo.finalPhaseDegrees = finalPhaseRaw * PHASE_UNIT;

                            // 获取并解析 3 字节的频点信息 (m 移动 3 字节)
                            int currentTagFreq = ((pEPCList[m++] & 0xFF) << 16) | ((pEPCList[m++] & 0xFF) << 8) | (pEPCList[m++] & 0xFF);
                            currentTagInfo.frequencyMHz = currentTagFreq / 1000.0;

                            // 读取用户数据 (每次都尝试读取，并更新为最后一次读取到的数据)
                            byte ENum = (byte) 255;
                            byte Mem = 1;
                            byte WordPtr = 2;
                            byte Num = 6;
                            byte[] Password = new byte[4];
                            MaskMem = 2;
                            MaskAdr[0] = 0;
                            MaskAdr[1] = 0;
                            MaskLen = (byte) (epclen * 8);
                            System.arraycopy(epc, 0, MaskData, 0, epclen);
                            byte[] Data = new byte[Num * 2];
                            int[] Errorcode = new int[1];

                            int readResult = reader.ReadData_G2(comAddr, epc, ENum, Mem, WordPtr, Num, Password,
                                                            MaskMem, MaskAdr, MaskLen, MaskData, Data, Errorcode, PortHandle[0]);

                            if (readResult == 0) {
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
                            } else {
                                currentTagInfo.userData = "Read Failed (" + readResult + ")";
                            }

                            tagDataMap.put(EPCstr, currentTagInfo); // 更新Map中的数据
                        }
                    }
                }

                // 在当前频点所有查询结束后，打印统计结果
                // System.out.println("\n--- Summary for Frequency Point " + freqPoint + " (" + frequencyMHz + " MHz) ---");
                if (tagDataMap.isEmpty()) {
                    System.out.println("No tags detected after " + NUM_QUERIES_PER_FREQ + " queries.");
                } else {
                    for (Map.Entry<String, TagInfo> entry : tagDataMap.entrySet()) {
                        String epc = entry.getKey();
                        TagInfo info = entry.getValue();

                        double successRate = (double) info.readCount / NUM_QUERIES_PER_FREQ;
                        double packetLossRate = 1.0 - successRate;

                        System.out.println("  Tag EPC: " + epc);
                        System.out.println("    Reads: " + info.readCount + "/" + NUM_QUERIES_PER_FREQ);
                        System.out.printf("    Packet Loss Rate: %.2f%%\n", packetLossRate * 100);
                        System.out.println("    RSSI: " + info.rssi + " dBm");
                        System.out.printf("    Initial Phase: %.2f°\n", info.initialPhaseDegrees);
                        System.out.printf("    Final Phase: %.2f°\n", info.finalPhaseDegrees);
                        System.out.printf("    Reported Frequency: %.3f MHz\n", info.frequencyMHz);
                        // System.out.println("    User Data: " + info.userData);
                        System.out.println("------------------------------------");
                    }
                }
            }
        }

        // Close serial port
        reader.CloseSpecComPort(PortHandle[0]);
        System.out.println("\nProgram execution completed, serial port closed");
    }
}