

pack = ax/data

src = \
  src/ax/data/KeyValueObjectServer.java \
  src/ax/data/KeyValueSerializer.java \
  src/ax/data/KeyValueDeserializer.java 

# javac -d obj -sourcepath src src/ax/data/*.java
# jar cmf manifest.txt bin/pbox.jar -C obj ax/data/KeyValueObjectServer.class

all: bin/pbox

obj/%.class:src/%.java
  javac -d obj -sourcepath src $< 

bin/pbox: $(patsubst obj/%.class,src/%.java,$(src))
  jar cmf ../manifst.txt ../$@ $(patsubst, %.class,obj/%.class,$^)




javac -d obj -sourcepath src src/ax/data/*.java
jar cmf manifest.txt bin/pbox.jar -C obj ax/data/


