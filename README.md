JDK 9 added the [jlink](https://docs.oracle.com/javase/9/tools/jlink.htm) tool that allows us to create 
a distributable custom JRE that containing only specific JDK modules our application needs. We no 
longer need to expect customers to have Java pre-installed or bundle the complete JRE (a few hundred MBs)
 with our app. I've created a few example apps where the uncompressed runtime is between 40MB to 90MB 
 depending upon external libraries used by the app. After zipping, the package is around 20-25 MB. For 
 me, this makes JVM a great platform for writing and distributing command-line apps comparable to 
 Python or Ruby.

In this post, we'll see how to create a simple "hello world" CLI written in Scala (although it's 
possible to write apps using any JVM language). This app will make an HTTP call using Java's 
new `java.net.http` module. 

**Note: You'll need JDK 9 or later to follow along this tutorial.**

# Create a new sbt project

I created a new sbt project using IntelliJ:

![New IntelliJ SBT Project](https://dev-to-uploads.s3.amazonaws.com/i/cdve2jtgvs6rejacf3cy.png)

Add `src/main/scala/App.scala` file that prints a message to the terminal when run:

```scala
object App {
  def main(args: Array[String]): Unit = {
    println("Hello, from scala command-line app")
  }
}
```

Let's test that our basic app works from the command line by running it via sbt:

```
~/t/simple-scala-cli> sbt run
......
[info] running App 
Hello, from scala command-line app
[success] Total time: 4 s
```

# Add sbt-native-packager plugin

[sbt-native-packager](https://www.scala-sbt.org/sbt-native-packager/index.html) is an sbt plugin 
that makes it easy to build packages for different operating systems. We'll use its 
[Jlink plugin](https://www.scala-sbt.org/sbt-native-packager/archetypes/jlink_plugin.html) to 
generate a custom JRE for our application.

Add or edit the `project/plugins.sbt` file to add the following:

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.0")
```

We need to enable the `JlinkPlugin` in `build.sbt`:

```
enablePlugins(JlinkPlugin)
```

Now, we can create a custom distribution via the following command:

```
~/t/simple-scala-cli> sbt stage
```

Our CLI app with a custom JRE is available in `target/universal/stage` directory:

![Custom JRE](https://dev-to-uploads.s3.amazonaws.com/i/rg4a4cecwrdqkwgepbzb.png)

Let's run our app from the `stage` directory:

```
~/t/simple-scala-cli> ./target/universal/stage/bin/simple-scala-cli 
Hello, from scala command-line app
```

Great! It works. But how can we be sure that it's not using the system wide Java installation on my 
machine? We can rename the `stage/jre` directory to something else and try again:

```
 ~/t/simple-scala-cli> mv ./target/universal/stage/jre ./target/universal/stage/xyz

~/t/simple-scala-cli> ./target/universal/stage/bin/simple-scala-cli
No java installations was detected.
Please go to http://www.java.com/getjava/ and download


~/t/simple-scala-cli> mv ./target/universal/stage/xyz ./target/universal/stage/jre

~/t/simple-scala-cli> ./target/universal/stage/bin/simple-scala-cli
Hello, from scala command-line app
```

Cool! It is indeed using the custom JRE image. On my machine the size of the stage directory is 46.9 
MB. After zipping it up, the size gets reduced to 19.3 MB.

> Let me reiterate - This 20MB zip file contains everything that your clients need to run your app. 
> JVM apps aren't that verbose anymore compared to standalone ruby/python apps that bundle their runtime!

# What does jlink plugin do?

The Jlink plugin performs two important steps in the build process. Here're the two relevant lines 
from the `sbt stage` command output:

```
[info] Running: jdeps --multi-release 13 -R /Users/parambirs/tmp/simple-scala-cli/
target/scala-2.13/classes /Users/parambirs/Library/Caches/Coursier/v1/https/
repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.1/scala-library-2.13.1.jar
```

It first runs `jdeps` to determine which java modules are being used by our codebase.

```
[info] Running: jlink --output /Users/parambirs/tmp/simple-scala-cli/target/jlink/output 
--add-modules java.base
```

It then runs `jlink` to build a custom JRE that includes all the Java modules our app will need. In 
this simple application, we are only using the `java.base` module.

# Making an HTTP call

Let's make our app do something more than just printing hello. We'll make the app fetch and print 
[google.ca](google.ca) home page HTML to the console. I'm not using any 3rd party HTTP client 
as [Java now comes with a built-in easy-to-use one](https://www.baeldung.com/java-9-http-client). 
Here's the Main class after the modifications:

```scala
import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}

object App {
  def main(args: Array[String]): Unit = {
    val uri = new URI("https://google.ca")

    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder(uri).build()
    val response = client.send(request, BodyHandlers.ofString())
    println(Console.YELLOW + response.body() + Console.RESET)
  }
}

```

Let's test our App :

```
sbt:simple-scala-cli> run
[info] Compiling 1 Scala source to  ...
[info] running App 
<HTML><HEAD><meta http-equiv="content-type" content="text/html;charset=utf-8">
<TITLE>301 Moved</TITLE></HEAD><BODY>
<H1>301 Moved</H1>
The document has moved
<A HREF="https://www.google.ca/">here</A>.
</BODY></HTML>
```
Okay, looks good to ship. Let's package it up:

```
sbt:simple-scala-cli> stage
...
[info] Running: jdeps --multi-release 13 -R /Users/parambirs/tmp/simple-scala-cli/
target/scala-2.13/classes /Users/parambirs/Library/Caches/Coursier/v1/https/
repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.1/scala-library-2.13.1.jar
...
[info] Running: jlink --output /Users/parambirs/tmp/simple-scala-cli/target/jlink/
output --add-modules java.base,java.net.http
```

As you can see here, `jdeps` identified that we need both `java.base` and `java.net.http` modules at runtime and `jlink` added them to our runtime image.

# Wrapping up

Seems like we're ready to ship our weather CLI to our clients. However, before we can do that, we need to make sure it works:

```
~/t/simple-scala-cli> ./target/universal/stage/bin/simple-scala-cli
Exception in thread "main" javax.net.ssl.SSLHandshakeException: Received fatal alert: handshake_failure
	at java.net.http/jdk.internal.net.http.HttpClientImpl.send(HttpClientImpl.java:568)
	at java.net.http/jdk.internal.net.http.HttpClientFacade.send(HttpClientFacade.java:119)
	at App$.main(App.scala:11)
	at App.main(App.scala)
Caused by: javax.net.ssl.SSLHandshakeException: Received fatal alert: handshake_failure
	at java.base/sun.security.ssl.Alert.createSSLException(Alert.java:131)
	at java.base/sun.security.ssl.Alert.createSSLException(Alert.java:117)
	at .......
        ......
```

Oops! Looks like we are missing something in our runtime JRE. I had to spend some time googling to 
fix this. We need `jdk.crypto.ec` module for SSL to work correctly, however, `jdeps` isn't able 
to figure this out. The solution is to add this jlink dependency in our `build.sbt` file:

```
jlinkModules += "jdk.crypto.ec"
```

Let's build and test our runtime image one more time:

```
~/t/simple-scala-cli [1]> sbt stage
...
[info] Running: jdeps --multi-release 13 -R /Users/parambirs/tmp/simple-scala-cli/target/
scala-2.13/classes /Users/parambirs/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/
org/scala-lang/scala-library/2.13.1/scala-library-2.13.1.jar
[info] Running: jlink --output /Users/parambirs/tmp/simple-scala-cli/target/jlink/output 
--add-modules java.base,java.net.http,jdk.crypto.ec
```

```
~/t/simple-scala-cli> ./target/universal/stage/bin/simple-scala-cli
<HTML><HEAD><meta http-equiv="content-type" content="text/html;charset=utf-8">
<TITLE>301 Moved</TITLE></HEAD><BODY>
<H1>301 Moved</H1>
The document has moved
<A HREF="https://www.google.ca/">here</A>.
</BODY></HTML>
```

Great! Finally we have a working and distributable command line app. After adding two extra modules, 
the runtime image size became 48.8MB uncompressed and 19.9MB when zipped.

Hope you found this useful.