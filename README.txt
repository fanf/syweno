
Author: François 'fanf' Armand
        http://fanf42.blogspot.com
        http://normation.com

License:  That project is under ASF 2.0 license, see the 
          provided "LICENSE-ASF-2.0.txt" file  or 
          http://www.apache.org/licenses/LICENSE-2.0.html

Use it at will, but don't complain if it brokes you hdtv. 

Key words
=========

LDAP, Replication, SyncRepl, Java LDAP SDK, Apache DS, 
UnboundID, Scala, Liftweb, Comet

What is it ?
============

This application is a show case about how to use two different
Java LDAP SDKs to get synchronization information from an LDAP
directory, thanks to "SyncRepl" protocol, standardized 
through RFC 4533 http://www.rfc-editor.org/rfc/rfc4533.txt

The two SDK are:
* the Apache DS SDK http://directory.apache.org/ (which is more 
  an internal API for the Apache Directory Server for now, but 
  evolves quickly)
  
* UnboundId LDAP SDK http://www.unboundid.com/products/ldapsdk ,
  a full feature, pure Java LDAP SDK. 


Some more details
=================

For each SDK, we build a service that handle a synchronization
connection to a master LDAP server and process synchronization
message and controls according to the SyncRepl protocol. 

The service allows listener services to be connected to the 
synchronization service and to receive (decoded) synchronization
messages.

For some more fun, the application is fully implemented in 
Scala 2.8.0, recently released: http://www.scala-lang.org/node/7009

The full application is a Lift web application that show in 
real-time synchronization message received from the LDAP server
in a searchable/sortable HTML grid. 

The web application plumbing is implemented as a listener service.
This is done with Lift a comet actor, which update a web page
 in real time with the synchronization information received. 


How to test ?
=============

Build tools
===========

This project use Maven 2, so if you don't have it yet, 
see http://maven.apache.org/

Install UnboundId from source
=============================
Syncrepl controls are only available since revision #162 of
UnboundId LDAP SDK, what is post 2.0.0-rc1, which is the most
recent binary revision to date (16 Jully 2010).

So, checkout the last svn revision, cd to the root directory.
Then compile.
You will have to have Ant installed, see http://ant.apache.org/
if it's not the case. 

Then, make the build script executable:
% chmod +x build-se.sh

And compile:
% ./build-se.sh

That should terminate without error, and create a "build/package"
directory with the maven jars. 
% cd build/package
% unzip maven.zip
Archive:  maven.zip
   creating: maven/
   creating: maven/com/
   creating: maven/com/unboundid/
   creating: maven/com/unboundid/unboundid-ldapsdk/
   creating: maven/com/unboundid/unboundid-ldapsdk/2.0.0/
   ....

Now, copy the tree to your maven local repository:
% cp -r maven/* ~/.m2/repository/

Done!

Test
====

Now, test: 
% mvn jetty:run

That should launch the application. 

But... wait, you also need an event producer and configure some 
properties to connect to it !

Configure properties
====================

"default.props" configuration file is located in 
directory "src/main/resources/props". It should be
rather well documented, and you only have 4 mandatory
configuration parameter to provide (if your LDAP server
is on localhost/389):
- Bind DN and password ;
- replication ID you want to use (if you don't have 
  any idea about what to choose, consider using 42, 
  it's a good answer)
- base DN of the search. 

Note: if you use a war version of the demo app, you
will need to unzip it, and then edit "default.props"
that should be located in "WEB-INF/classes" directory 
before rezipping the war. Yes, it's awful. 

Install OpenLDAP with syncrepl
==============================

* install an OpenLDAP, and add some datas to it. 
  Details of that operation are let as a game for
  the user ;)
  
* configure OpenLDAP to be a syncrepl master. 
  See: http://www.openldap.org/doc/admin24/replication.html

  For a Debian based distribution it should be as easy as
  editing the OpenLDAP slapd.conf config file, to add: 
  
  8<-------------------------------------------------
  ########
  # Load syncrepl dynamic module
  moduleload      syncprov

  ####
  # Add that at the end of a database definition
  ####
  
  #syncrepl provider configuration
  overlay syncprov
  syncprov-checkpoint 100 2
  syncprov-sessionlog 100
  syncprov-reloadhint TRUE
  8<-------------------------------------------------

OK, and now test !
