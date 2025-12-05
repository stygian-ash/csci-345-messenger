# TCP Instant Messenger

An instant messenger with authentication, user statuses and peer-to-peer file transfer.

Final project for *CSCI 345: Computer Networks*.

## Invocation
1. Open the repository in [IntelliJ](https://www.jetbrains.com/idea/download/)
1. On one machine, run src/messenger/Server.java
1. On other machines, run src/messenger/Client.java

To run all processes on the same machine,
pass distinct port arguments to each Client session:
```bash
java src/messenger/Client.java 1234 # Port must not be 6789
```

## Sample Session
```
/server 127.0.0.1
/register alice password123
/getstatus bob
User bob: READY
/connect bob
hi bob!
>hi alice!
/getstatus bob
User bob: CHATTING
/sendfile file.txt cool_file.txt
File sent successfully!
/quit
Bye.
```
