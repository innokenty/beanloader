package ru.qatools.beanloader.internal;

import javax.xml.bind.JAXB;
import java.io.File;

/**
 * @author Innokenty Shuvalov innokenty@yandex-team.ru
 */
public class FileLoadStrategy<T> extends BeanLoadStrategy<T> {

    private final File file;
    private final boolean reload;

    public FileLoadStrategy(File file, boolean reload) {
        super();
        this.file = file;
        this.reload = reload;
    }

    @Override
    protected boolean canUnmarshal() {
        return file.exists();
    }

    @Override
    protected boolean reloadEveryTime() {
        return reload;
    }

    @Override
    protected T performUnmarshal(Class<T> beanClass) {
        return JAXB.unmarshal(file, beanClass);
    }

    @Override
    protected String getSourceDescription() {
        return "file " + file.getPath();
    }
}
