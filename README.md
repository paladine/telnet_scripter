# telnet_scripter
Telnet passthrough program which allows you run scripts (any program using stdin/stdout), useful for playing telnet-based MUDs

## How to build?
Install [bazel](https://bazel.build/)

To build a deployable jar, run `bazel build //java/com/jeffreys/telnet:TelnetScripter_deploy.jar`

## How to test?
To execute tests, run `bazel test //javatests/com/jeffreys/telnet:all`

## How to execute?
`java -jar TelnetScripter_deploy.jar <arguments>`
  * --remote_host=remote host to connect to
  * --remote_port=remote port to connect to, default 23
  * --local_port=local port to listen on, default 2112
  
You can also run out of the repo directory, `bazel run //java/com/jeffreys/telnet:TelnetScript -- <arguments>`

## How do you run a script?
Once you're connected to a system, you just type in the magic command `#!script <script_path>` and hit enter. It should launch the script file with the telnet connection as stdin and any writes to stdout will go to the telnet host.

A script can be written in any language since I/O is simply through stdin/stdout. 

Here's a simple script that writes something every 5 seconds

```
#!bin/bash

while :
do
  sleep 5
  echo "Hi, I'm sending data!"
done
```

## How to stop your script?
Just kill the script process in your OS. You cannot stop it via special text commands.
