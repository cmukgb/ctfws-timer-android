###############################################
Capture The Flag With Stuff Android Application
###############################################

The `CMU KGB <http://www.cmukgb.org/>`_ plays a delightful game called
`Capture The Flag With Stuff <http://www.cmukgb.org/ctfws/>`_.  This app
displays game state information, just like the hardware timers we have
built.

Privacy Information
###################

This app does not attempt to access traditionally private information
(contacts, phone identity, file system, ...) nor does it access cameras,
microphones, or other sensors of the device, except for the clock.  It does,
however, request access to the network status information of the device and,
of course, access to the Internet.  When connecting to the CMU KGB's CtFwS
MQTT server, of course, the device's IP address may become visible to the
CMU KGB's server, just as if it had visited the CMU KGB website.  If the
settings are changed to update which server the app attempts to use, that
server will also, similarly, see the device's IP address.

Speaking of accessing websites, the application will, when requested by the
MQTT server, grab a small file from a server-provided URL to update the
handbook HTML it has cached.  This may reveal the device's IP address to
another server.
