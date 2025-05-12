import socket
import pyvjoy
import time
import os

# Initialize vJoy device
j = pyvjoy.VJoyDevice(1)

# UDP settings
UDP_IP = "192.168.1.189"
UDP_PORT = 5005

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind((UDP_IP, UDP_PORT))
print("Server config 3 launched")
print(f"Listening for data on {UDP_IP}:{UDP_PORT}...")

def map_roll_to_axis(roll):
    # Invert roll direction for correct left/right steering in TrackMania
    roll = max(-90, min(90, -roll))  # Invert the roll value here
    return int(((roll + 45) / 90) * 32768)

def trigger_restart_button():
    # Simulate pressing button 1 (change if your game expects a different button)
    RESTART_BUTTON = 1
    j.set_button(RESTART_BUTTON, 1)  # Press
    time.sleep(0.1)                 # Short delay
    j.set_button(RESTART_BUTTON, 0)  # Release

while True:
    data, addr = sock.recvfrom(1024)
    try:
        decoded = data.decode().strip()

        if decoded.upper() == "RESTART":
            trigger_restart_button()
            continue

        roll_str, accel_str, brake_str = decoded.split(',')
        roll = int(roll_str)
        accelerate = int(accel_str)
        brake = int(brake_str)

        x_val = map_roll_to_axis(roll)

        j.set_axis(pyvjoy.HID_USAGE_X, x_val)
        j.set_axis(pyvjoy.HID_USAGE_Y, accelerate)
        j.set_axis(pyvjoy.HID_USAGE_Z, brake)
        os.system('cls')
        print(f"ROLL: {roll}, ACCEL: {accelerate}, BRAKE: {brake}")
    except Exception as e:
        print(f"Error: {e}")
