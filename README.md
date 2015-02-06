CDMI for Swift
--------------

This is an implementation of [CDMI 1.0.2](http://cdmi.sniacloud.com/)
for [OpenStack Swift](http://swift.openstack.org/). It is based on the
[CDMI v1.0.2 Skeleton Server](https://github.com/grnet/cdmi-spec)

Test
----

In order to start testing the code quickly, you can run it with the
following command. The configuration of the service port is only given
as an example of how to pass arguments to the server.

    mvn scala:run -DmainClass=de.fzj.openstack.swift.CdmiSwiftServer -DaddArgs="-gr.grnet.cdmi.service.port=:8081"

This will start a server for you, against which you can test CDMI
client commands either by a dedicated client or via curl, e.g.:

    curl localhost:8081/cdmi_capabilities/

In order to get the full list of possible arguments for starting up
the server., try the following:

    mvn scala:run -DmainClass=de.fzj.openstack.swift.CdmiSwiftServer -DaddArgs="-help"
