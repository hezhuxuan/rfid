import re
import matplotlib.pyplot as plt
import numpy as np

# All frequency points to check
all_frequencies = [
    840125, 840375, 840625, 840875, 841125, 841375, 841625, 841875,
    842125, 842375, 842625, 842875, 843125, 843375, 843625, 843875,
    844125, 844375, 844625, 844875, 920125, 920375, 920625, 920875,
    921125, 921375, 921625, 921875, 922125, 922375, 922625, 922875,
    923125, 923375, 923625, 923875, 924125, 924375, 924625, 924875
]

def parse_rfid_file(filename):
    """
    Parse RFID data file and extract Packet Loss Rate and RSSI for each frequency
    """
    packet_loss_rates = {}
    rssi_values = {}

    with open(filename, 'r', encoding='utf-8') as file:
        lines = file.readlines()

    current_freq = None
    current_plr = None
    current_rssi = None

    for line in lines:
        line = line.strip()

        # Look for frequency line
        if 'Reported Frequency:' in line:
            freq_match = re.search(r'Reported Frequency: (\d+\.\d+) MHz', line)
            if freq_match:
                freq_str = freq_match.group(1)
                current_freq = int(float(freq_str) * 1000)

        # Look for packet loss rate
        elif 'Packet Loss Rate:' in line:
            plr_match = re.search(r'Packet Loss Rate: (\d+\.\d+)%', line)
            if plr_match:
                current_plr = float(plr_match.group(1))

        # Look for RSSI
        elif 'RSSI:' in line:
            rssi_match = re.search(r'RSSI: (-?\d+) dBm', line)
            if rssi_match:
                current_rssi = int(rssi_match.group(1))

        # When we have all three values, store them
        if current_freq is not None and current_plr is not None and current_rssi is not None:
            packet_loss_rates[current_freq] = current_plr
            rssi_values[current_freq] = current_rssi
            # Reset for next detection
            current_freq = None
            current_plr = None
            current_rssi = None

    return packet_loss_rates, rssi_values

def create_visualization(filename):
    """
    Create visualization of Packet Loss Rate and RSSI across all frequencies
    """
    packet_loss_rates, rssi_values = parse_rfid_file(filename)

    # Prepare data for all frequencies
    frequencies = []
    plr_data = []
    rssi_data = []

    for freq in all_frequencies:
        frequencies.append(freq)
        # Set packet loss rate to 100% and RSSI to 0 for frequencies without tag detection
        plr_data.append(packet_loss_rates.get(freq, 100.0))
        rssi_data.append(rssi_values.get(freq, 0))

    # Create the plot
    fig, ax1 = plt.subplots(figsize=(16, 8))

    # Plot Packet Loss Rate
    color1 = 'tab:red'
    ax1.set_xlabel('Frequency (MHz)')
    ax1.set_ylabel('Packet Loss Rate (%)', color=color1)

    # Create x-axis positions for all frequencies (evenly spaced)
    x_positions = list(range(len(all_frequencies)))

    # Plot all data points using evenly spaced positions
    line1 = ax1.plot(x_positions, plr_data, color=color1, marker='o', linestyle='-', linewidth=2, markersize=4, label='Packet Loss Rate')
    ax1.tick_params(axis='y', labelcolor=color1)
    ax1.set_ylim(0, 100)
    ax1.grid(True, alpha=0.3)

    # Set x-axis ticks and labels, skipping 850-920MHz range
    x_ticks = []
    x_tick_labels = []
    for i, freq in enumerate(all_frequencies):
        freq_mhz = freq / 1000
        # Skip frequencies between 850MHz and 920MHz
        if 850 <= freq_mhz <= 920:
            continue
        x_ticks.append(i)
        x_tick_labels.append(f'{freq_mhz:.3f}')

    ax1.set_xticks(x_ticks)
    ax1.set_xticklabels(x_tick_labels, rotation=45)

    # Create second y-axis for RSSI
    ax2 = ax1.twinx()
    color2 = 'tab:blue'
    ax2.set_ylabel('RSSI (dBm)', color=color2)
    line2 = ax2.plot(x_positions, rssi_data, color=color2, marker='s', linestyle='--', linewidth=2, markersize=4, label='RSSI')
    ax2.tick_params(axis='y', labelcolor=color2)

    # Set RSSI range to 40-70 dBm
    ax2.set_ylim(30, 100)

    # Add legend
    lines = line1 + line2
    labels = [l.get_label() for l in lines]
    ax1.legend(lines, labels, loc='upper right')

    # Set title and improve layout
    plt.title(f'RFID Performance Analysis - {filename}', fontsize=14, fontweight='bold')
    plt.xticks(rotation=45)
    plt.tight_layout()

    # Save the plot
    output_filename = filename.replace('.txt', '_analysis.png')
    plt.savefig(output_filename, dpi=300, bbox_inches='tight')
    plt.show()

    print(f"Visualization saved as: {output_filename}")

    # Print summary statistics
    detected_frequencies = [freq for freq in all_frequencies if freq in packet_loss_rates]
    print(f"\nSummary:")
    print(f"Total frequencies checked: {len(all_frequencies)}")
    print(f"Frequencies with tag detection: {len(detected_frequencies)}")
    print(f"Frequencies without detection: {len(all_frequencies) - len(detected_frequencies)}")

    if detected_frequencies:
        print(f"\nBest performance (lowest packet loss):")
        best_plr_freq = min(packet_loss_rates, key=packet_loss_rates.get)
        print(f"  Frequency: {best_plr_freq/1000:.3f} MHz, PLR: {packet_loss_rates[best_plr_freq]:.2f}%, RSSI: {rssi_values[best_plr_freq]} dBm")

        print(f"\nWorst performance (highest packet loss):")
        worst_plr_freq = max(packet_loss_rates, key=packet_loss_rates.get)
        print(f"  Frequency: {worst_plr_freq/1000:.3f} MHz, PLR: {packet_loss_rates[worst_plr_freq]:.2f}%, RSSI: {rssi_values[worst_plr_freq]} dBm")

if __name__ == "__main__":
    # Example usage
    filename = "one_epc_straight.txt"
    create_visualization(filename)