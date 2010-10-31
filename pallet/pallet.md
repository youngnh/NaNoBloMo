# Starting a Cluster on EC2 with Pallet

[Pallet](http://hugoduncan.github.com/pallet/) is a node provisioning, configuration and administration tool.  It is designed to make small to midsize deployments simple.

Pallet is built on top of the java library [jclouds](http://code.google.com/p/jclouds/).  There is a Clojure API for jclouds, written by the same contributors that have put in so much work on Pallet, and it's what Pallet itself uses, for the most part.  jclouds seems to be more focused on provisioning and controlling compute instances, while Pallet is a configuration and administration tool that just happens to need that provisioning capability.

## Getting started

The Pallet docs are pretty good, but they left me with some questions and produced some behavior that I had to investigate further to understand.  In that way, Pallet is currently a bit of a narrow path on top of steep cliffs, wander off the trail too far and things get choppy.  To get started with Pallet, I cloned Hugo Duncan's repo at `git://github.com/hugoduncan/pallet.git` and started a swank server with `lein swank`. Easy as could be.

## Node Types

Pallet has a notion of nodes, identically configured machines.  A node is identified by a tag and specified up front how it's supposed to be configured.  A plain vanilla node with no configuration or other special customizations can be created via `defnode`:

    (defnode vanilla {})

You specify a node's configuration via _phases_.  `:bootstrap` and `:configure` are the two major phases of a node.  `:bootstrap` is run exactly once when a node is started for the first time, useful for setting up users, basic services and settings to grant access to the box. `:configure` is a bit more general and can be thought of as the steps needed to bring a node to a baseline configuration.

I wanted to start a 5 machine CouchDB cluster, so I defined a couchdb node type:

    (defnode couchnode {}
      :bootstrap (phase (automated-admin-user))
      :configure (phase (java :openjdk)
      		 	(couchdb)))

Node names can't have hypens in them, so scrunch everything together. The `phase` calls are a macro that run packaged configurations called crates.  The above adds my public key (`~/.ssh/id_rsa.pub`) as authorized to login to a couchdb node, and then installs the Open JDK and CouchDB.  Cool thing about this is that we don't specify and it doesn't matter what flavor of Linux is running on the node, these crates are agnostic.

## Logging into the Box

jclouds, not Pallet as far as I can tell, when targeted at EC2 creates a new security group per tag (nodetype) and a new key pair per box.  This is good security practice, but it wasn't obvious to me how to get the actual key so that I could log into a node and pooke around.

Poking around on individual nodes is what Pallet is supposed to save you from, so much later in your comfortability with the tool I'm sure that this sort of thing doesn't matter, but it's an issue I encountered.

The `automated-admin-user` crate is incredibly useful in this regard.  It authorizes a public key for login in addition to the one that jclouds creates.  By default it authorizes `~/.ssh/id_rsa.pub`, your public key, but you can supply your own along with a few more advanced options defined in the `pallet.crate.ssh-key` namespace.

## Beefier Boxes

The couchdb node we defined above by default uses the smallest instance available.  On EC2, this is the t1.micro instance.  If we need more memory or want more cores, we need to specify that in our node definition.

    (defnode couchnode {:min-ram (* 7 1024) :min-cores 2}
      :bootstrap (phase (automated-admin-user))
      :configure (phase (java :openjdk)
      		 	(couchdb)))

The Pallet docs don't do a stellar job of outlining what these options are, but that may be because the options themselves are specified in the jclouds Clojure API source, in `jclouds/compute/src/main/clojure/org/jclouds/compute.clj`:

    os-family 
    location-id 
    architecture 
    image-id 
    hardware-id
    os-name-matches 
    os-version-matches
    os-description-matches
    os-64-bit
    image-version-matches 
    image-name-matches
    image-description-matches 
    min-cores 
    min-ram
    run-script 
    install-private-key 
    authorize-public-key
    inbound-ports 
    smallest 
    fastest 
    biggest 
    any 
    destroy-on-error

On EC2, with only the RAM and Cores specified, the Amazon Machine Image (AMI) is left to the library to choose.  I think jclouds (I think Pallet offloads this logic) chose poorly.  The machine is Ubuntu 9.02, whose ssh daemon takes forever to start (consistently more than 10 minutes for me), and once it is started, jclouds itself seemed to be locked out, all of its operations failing with an "Auth failure" message.  So as a final step, I found a fresh Ubuntu 10.04 image and specified that along with the other information:

    (defnode couchnode {:image-id "us-east-1/ami-da0cf8b3" :min-ram (* 7 1024) :min-cores 2}
      :bootstrap (phase (automated-admin-user))
      :configure (phase (java :openjdk)
      	   	        (couchdb)))

## Converge and Lift

With the node type specified, it's time to start up a few instances.  This is the job of `converge`.  First, we create a `compute-service` object and then we're good to go:

    (def ec2-service (compute-service "ec2" :identity ec2-access-id :credential ec2-secret-key)

    (converge {couchnode 5} :compute ec2-service)

`converge` is kind of neat as it will stop or start new nodes as necessary to bring the total number specified per nodetype.  It runs `:bootstrap` for newly started nodes and `:configure` for all nodes to ensure that when it completes that you have a homogenous cluster of machines at your disposal.

There is a similar operation, `lift` that applies phases to all of the nodes of a certain type.  It looks very similar in form to `converge`, but it doesn't guarantee to run `:configure` unless you explicitly specify that it should.  This makes `lift` a good candidate for applying configurations to a set of nodes after they've already been started:

    (lift couchnode :compute ec2-service
          :phase (phase (jetty)))

There's a couple of namespace imports that you need to make to be able to run the above code as it exists above, you can get your hands on the whole thing [here](http://gist.github.com/655752).