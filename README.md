![image](https://github.com/user-attachments/assets/530ae151-422d-4783-a974-362834a3871e)








**Command Runner** is a Burp Suite extension that enables you to run system commands directly within Burp’s interface. It supports multiple tabs, a command playbook to save commands, and real-time output display, helping penetration testers integrate OS tools seamlessly into burpsuite.

---

## Features

- Execute OS commands from inside Burp Suite 
- Multiple independent command tabs
- Manage a command playbook with save/delete functionality
- Real-time command output display
- Cancel running commands anytime

---

## Installation

**Requirements:** Burp Suite Professional or Community Edition (v2025.6 or later)

### Option A — Pre-built jar (easiest)

1. Download `command-runner-1.0.0.jar` from this repository's "Releases".
2. Open Burp Suite → **Extensions** → **Add**
3. Set Extension type to **Java**, select the downloaded jar → **Next**
4. You should see a **Command Runner** tab in Burp Suite.

### Option B — Build from source

1. Clone the repo and make sure you have Java 17+ and Gradle installed.
2. Run:
   ```bash
   ./gradlew jar
   ```
3. The jar will be at `java/build/libs/command-runner-1.0.0.jar`
4. Load it in Burp Suite as above.
  
     ![image](https://github.com/user-attachments/assets/e6530413-3856-4a8a-8af4-c11b69449a27)


2. **Prepare Command Playbook**

   - It’s recommended to edit commands.txt directly to organize your saved commands more effectively. Using the save command function will simply add the new command to the bottom of the list, which can make organization harder.
   - Edit `commands.txt` to add your frequently used commands (one per line)
   - An example format is provided in commands.txt, edit it to your liking.

     ![image](https://github.com/user-attachments/assets/20966310-44fc-4d39-960d-4e2405f67c99)

   - `commands.txt` is created in Burp Suite's working directory — on macOS/Linux this is typically your home directory (`~`). You can find it by running `find ~ -name "commands.txt" -type f` in your terminal. Place or edit it there for your saved commands to load on startup.
   - Now you should be easily able to run the commands you need just change up the target.
     
     ![image](https://github.com/user-attachments/assets/c36ce7ff-19cf-4491-960c-60b081cf3b76)


---

## How to Use

1. **Open the "Command Runner" tab** in Burp Suite after loading the extension.

2. **Manage Tabs:**
   - Click **+ New Tab** to open multiple command runners.
   - Each tab runs commands independently.

3. **Run Commands:**
   - Enter a command in the input field (e.g., `nmap -sV target.com`)
   - Click **Run command** to execute it.
   - View real-time output in the pane below.
   - Make sure all the tools you'd like to use are already installed on your system and on path.

4. **Use the Command Playbook:**
   - Select a saved command from the dropdown.
   - Click **Save Command** to add the current command to the playbook.
   - Click **Delete Command** to remove a selected command.
   - Commands persist in `commands.txt` across sessions.

5. **Cancel or Close:**
   - Use **Cancel** to stop a running command.
   - Use **Close Tab** to remove the current tab.

6. **Resizing**
   - click on the small arrows to resize 
     
     ![image](https://github.com/user-attachments/assets/b382bff0-f217-4d3f-bc3f-dd5621d786b6)


---

## Security Notice

- Commands run with your local user privileges.
- Avoid running untrusted or dangerous commands.
- Use only on authorized targets.

---
