compile:
	mvn compile assembly:single

run:
	java -cp target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar socs.network.Main conf/router1.conf

