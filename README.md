# BeanLoader

[![release](http://github-release-version.herokuapp.com/github/yandex-qatools/beanloader/release.svg?style=flat)](https://github.com/yandex-qatools/beanloader/releases/latest) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/ru.yandex.qatools.beanloader/beanloader/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/ru.yandex.qatools.beanloader/beanloader)
[![covarage](https://img.shields.io/sonar/http/sonar.qatools.ru/ru.yandex.qatools.beanloader:beanloader/coverage.svg?style=flat)](http://sonar.qatools.ru/dashboard/index/1203)

This small library with no dependencies provides an easy fluent API to load xml beans
from different sources via JAXB, giving some of them higher priority
than to the others.

BeanLoader is useful if you are facing one of the following cases:

1. You need to load a bean from a range of locations and stop, when you load the bean successfully. 
For example, when you need to load a bean from a file, but in case the file is missing 
you've got a classpath resource to use instead. 
Or another file etc.
2. You need to reload the bean multiple times from the same resource. 
Of course, you can just write a ```fetchAndGetMyBean()``` and call it over and over again, but why?
3. You want to use a ```java.nio.file.WatchService``` to load the bean every time it is changed externally.
4. You want to load multiple xml files of the same bean class from a directory by pattern,
   but you don't wanna fetch the list of files and iterate yourself.

### Maven

```xml
<dependency>
    <groupId>ru.yandex.qatools.beanloader</groupId>
    <artifactId>beanloader</artifactId>
    <version>2.0</version>
</dependency>
```

### Functions and Usage

#### 1) Basic unmarshalling
This code

```java
Bean bean = (Bean) JAXB.unmarshal(this.getClass().getClassLoader().getResource("bean.xml"), Bean.class);
```

is equivalent to:

```java
import static ru.qatools.beanloader.BeanLoader.load;
import static ru.qatools.beanloader.BeanLoaderStrategies.resource;

Bean bean = load(Bean.class).from(resource("bean.xml")).getBean();
```

And this

```java
Bean bean = (Bean) JAXB.unmarshal(new File("/etc/bean.xml"), Bean.class);
```

to this:

```java
Bean bean = load(Bean.class).from(file("/etc/bean.xml")).getBean();
```

#### 2) Loading a bean from a list of sources

This is the main function of BeanLoader. As it is said above, the goal is to 
simplify the choise of the source to load the xml bean from.

```java
import static ru.qatools.beanloader.BeanLoaderStrategies.*;
import static ru.qatools.beanloader.BeanLoader.*;

BeanLoader<Bean> beanLoader = load(Bean.class)
        .from(resource("bean.xml"))
        .from(url("http://example.com?get-my-bean-dawg"))
        .from(file("~/beans/bean.xml"))
        .from(fileWithWatcher("/etc/beans/", "bean.xml"));

// load bean iterating over the given strategies
// until one of the returns a non-null bean
Bean bean = beanLoader.getBean();
makeSomeStuff(bean);

// reload the bean, if reloads are specified for any strategy
// returns the same object if no reloads are specified
bean = beanLoader.getBean();
makeAnotherStuff(bean);
```

#### 3) Reloading a file every time

Lets say you want to reload fresh data for your bean every time some your method is called. 
Of course you could just insert the load-n-unmarshal code right into your method 
but it may not be convenient for multiple reasons. 
With BeanLoader you can organize your code as follows:

```java
import static ru.qatools.beanloader.BeanLoader.load;
import static ru.qatools.beanloader.BeanLoaderStrategies.*;

public class MyClass {

    private final BeanLoader<Bean> beanLoader;

    public MyClass(String filename) {
        this.beanLoader = load(Bean.class).from(file(filename, true));
    }
    
    public void doSomeStuff() {
        // do some other stuff
        Bean bean = beanLoader.getBean();
        // do some stuff with the bean
    }
}
```

The second boolean parameter here indicates that a file will be reloaded 
on every call to ```beanLoader.getBean()``` method.

#### 4) Using a file watcher

Now suppose realoading a file every time doesn't suit your needs 
and you want to use a ```java.nio.file.WatchService``` to change the loaded bean 
only when it gets changed externally. Instead of implementing your own thread 
with ```while(true)``` loop you can just do it in a couple of lines:

```java
import static ru.qatools.beanloader.BeanLoader.load;
import static ru.qatools.beanloader.BeanLoaderStrategies.*;

public class MyClass {

    private final BeanLoader<Bean> beanLoader;

    public MyClass(String directory, String filename) {
        this.beanLoader = load(Bean.class).from(fileWithWatcher(directory, filename));
    }

    public void doSomeStuff() {
        Bean bean = beanLoader.getBean();
        // do some stuff with the bean
    }
}
```

And that's it! The bean will be reloaded only when it is changed and you'll get 
the fresh version of your bean on every call to ```beanLoader.getBean()``` guaranteed.
 Although remember that not every platform supports watching files.

#### 5) Using a file watcher with a listener

Sometimes you need do something with the bean immediately when it changes. 
For example, log it's contents or fire some message. This can be achieved 
with the help of ```BeanChangeListener``` interface. See the following code:

```java
import static ru.qatools.beanloader.BeanLoader.load;
import static ru.qatools.beanloader.BeanLoaderStrategies.*;

public class MyClass implements BeanChangeListener<Bean> {

    private final BeanLoader<Bean> beanLoader;

    public MyClass(String directory, String filename) {
        this.beanLoader = load(Bean.class).from(fileWithWatcher(directory, filename, this));
    }

    public void doSomeStuff() {
        Bean bean = beanLoader.getBean();
        // do some stuff with the bean
    }

    @Override
    public void beanChanged(Path path, Bean newBean) {
        System.out.println("Wow, new bean is here! Take a look: " + stringify(newBean));
    }
}
```

Notice that if you lose a link to the beanLoader instance — the watcher thread may get stopped
somewhere in the future when the garbage collection happens. That's a subject of discussion though
maybe one can think of some better behaviour for when to stop the thread.

#### 6) Using a file watcher without BeanLoader

Imagine you do not need any beanLoader, all you want to do — is to be notified on every bean change. 
Due to the reasons described above there is some one more class to fill the functionality gap.
Of course you can just take the example above, delete the ```doSomeStuff()``` method and 
yeah, it will work. Although you'll need to preserve the field which will be marked as unused by
any IDE. That may cause problems when another developer will delete the field by mistake and
then get severely surprised when the file watching thread stops. There is a special parameter 
for this case though: you can pass ```true``` as a forth parameter to ```fileWithWatcher()``` method
and this way you'll prevent the watcher thread from stopping when the ```beanLoader``` instance
get garbage collected. Anyway, using this parameter is equivalent to the code snippet below:

```java
import static ru.qatools.beanloader.BeanWatcher.watchFor;

public class MyClass implements BeanChangeListener<Bean> {

    public MyClass(String directory, String filename) throws IOException {
        watchFor(Bean.class, directory, filename, this);
    }

    @Override
    public void beanChanged(Path path, Bean newBean) {
        System.out.println("Wow, new bean is here! Take a look: " + stringify(newBean));
    }
}
```

Note that when you call the ```watchFor``` method  your listener will be invoked immediately
for the current version of the file. Because no one needs to be notified of changed without
reading the initial content first.

Also note that if the file content at some point will not match the bean class provided -
then the listener will be invoked with ```null``` as the second argument. The same behavior
is expected when the watched file is deleted.

#### 7) Watching over multiple files

The same way you can also watch over multiple files, specifying them all by pattern:

```java
import static ru.qatools.beanloader.BeanWatcher.watchFor;

public class MyClass implements BeanChangeListener<Bean> {

    public MyClass(String directory) throws IOException {
        watchFor(Bean.class, directory, "*-config.xml", this);
    }

    @Override
    public void beanChanged(Path path, Bean newBean) {
        System.out.println("Wow, new bean is here! Take a look: " + stringify(newBean));
    }
}
```

Pattern should match the rules described in the ```java.nio.file.FileSystem.getPathMatcher()``` 
[method javadoc][1] for the ```glob``` syntax. And yeah, again: the listener will be immediately 
invoked for all the the files that match that glob.

#### 8) Loading multiple beans once and at once

...or if you don't wanna watch for changes but just load all the beans in a directory once —
you can just go:

```java
BeanLoader.loadAll(Bean.class, directory, "*-config.xml", new BeanChangeListener<Bean>() {
    @Override
    public void beanChanged(Path path, Bean newBean) {
        System.out.println("Bean " + path + " loaded: " + stringify(newBean));
    }
});
```

[1]: http://docs.oracle.com/javase/7/docs/api/java/nio/file/FileSystem.html#getPathMatcher%28java.lang.String%29
