# RFID DATA REQUIRE
rfid_out_txt.java ------ 输出为txt

rfid.java ------ print 在终端



# RFID Data Processing Tool

This tool processes RFID tag data from text files and organizes it into Excel spreadsheets.

## Features

- Parses RFID tag data from text files
- Organizes data by tag EPC and frequency
- Generates Excel spreadsheets with formatted output
- Supports multiple frequencies per tag

## Usage

### Basic Usage
```bash
python process_rfid_data.py <input_file.txt>
```

### Example
```bash
python process_rfid_data.py test.txt
```

This will create `test_processed.xlsx` with the organized data.

## Input File Format

The script expects text files with the following format:

```
Tag EPC: E280F336200000000042FE29
    Reads: 2/5
    Packet Loss Rate: 60.00%
    RSSI: 71 dBm
    Initial Phase: 319.38°
    Final Phase: 315.90°
    Reported Frequency: 840.125 MHz
------------------------------------
```

## Output Format

The generated Excel file will have:
- **First column**: Tag EPC names
- **First row**: Frequencies (MHz)
- **Second row**: Parameter names (RSSI, Loss Rate, Initial Phase, Final Phase)
- **Data cells**: Corresponding values for each tag at each frequency

## Requirements

- Python 3.6+
- Required packages:
  - pandas
  - openpyxl

Install dependencies:
```bash
pip install pandas openpyxl
```

## Output Example

The Excel file will be formatted with:
- Header rows with background colors
- Bold formatting for tag names
- Centered alignment for headers
- Auto-adjusted column widths
