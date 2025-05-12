import socket
import pyvjoy
import time

# Initialize vJoy device
j = pyvjoy.VJoyDevice(1)

# UDP settings
UDP_IP = "192.168.1.189"
UDP_PORT = 5005

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind((UDP_IP, UDP_PORT))
print("SuperTuxKart server config launched")
print(f"Listening for data on {UDP_IP}:{UDP_PORT}...")

def map_roll_to_axis(roll):
    # Invert roll direction for correct left/right steering in TrackMania
    roll = max(-45, min(45, -roll))  # Invert the roll value here
    return int(((roll + 45) / 90) * 32768)

def compute_y_axis(accelerate, brake):
    # TrackMania: Y = 0 (forward), 16384 (neutral), 32768 (reverse)
    if brake > 0 and not accelerate > 0: # Swapped accelerate and brake conditions
        return 32768   # Full forward (when brake is pressed in app)
    elif accelerate > 0 and not brake > 0: # Swapped accelerate and brake conditions
        return 0  # Full reverse/brake (when accelerate is pressed in app)
    else:
        return 16384   # Neutral

def trigger_restart_button():
    # Simulate pressing button 1 (change if your game expects a different button)
    RESTART_BUTTON = 1
    print(">>> RESTART command received! Triggering virtual button.")
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
        print(decoded)
        roll_str, accel_str, brake_str = decoded.split(',')
        roll = int(roll_str)
        accelerate = int(accel_str)
        brake = int(brake_str)

        x_val = map_roll_to_axis(roll)
        y_val = compute_y_axis(accelerate,brake)


        j.set_axis(pyvjoy.HID_USAGE_X, x_val)
        j.set_axis(pyvjoy.HID_USAGE_Y, y_val)


        print(f"ROLL: {roll}, ACCEL: {accelerate}, BRAKE: {brake} => X: {x_val}, Y: {y_val}")
    except Exception as e:
        print(f"Error: {e}")
