/* (c) 2014-2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2014 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.platform.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.util.logging.Logging;

/**
 * Utility class for File handling code. For additional utilities see IOUtils.
 * <p>
 * This utility class focuses on making file management tasks easier for
 * ResourceStore implementors.
 * 
 * @since 2.5
 */
public final class Files {
    
    /**
     * Quick Resource adaptor suitable for a single file.
     * <p>
     * This can be used to handle absolute file references that are not located
     * in the data directory.
     */
    static final class ResourceAdaptor implements Resource {
        
        final File file;

        private ResourceAdaptor(File file) {
            this.file = file.getAbsoluteFile();
        }

        @Override
        public String path() {
            return Paths.convert(file.getPath()); 
        }

        @Override
        public String name() {
            return file.getName();
        }

        @Override
        public Lock lock() {
            return new Lock() {
                public void release() {
                }
            };
        }
        @Override
        public void addListener(ResourceListener listener) {
            watcher.addListener(path(), listener);
        }
        @Override
        public void removeListener(ResourceListener listener) {
            watcher.removeListener(path(), listener);
        }

        @Override
        public InputStream in() {
            final File actualFile = file();
            if (!actualFile.exists()) {
                throw new IllegalStateException("Cannot access " + actualFile);
            }
            try {
                return new FileInputStream(actualFile);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public OutputStream out() {
            final File actualFile = file();
            if (!actualFile.exists()) {
                throw new IllegalStateException("Cannot access " + actualFile);
            }
            // first save to a temp file
            final File temp;
            synchronized(this) {
                File tryTemp;
                do {
                    UUID uuid = UUID.randomUUID();
                    tryTemp = new File(file.getParentFile(), String.format("%s.%s.tmp", file.getName(), uuid));
                } while(tryTemp.exists());
                
                temp = tryTemp;
            }
            try {
                temp.createNewFile();
                // OutputStream wrapper used to write to a temporary file
                return new OutputStream() {
                    FileOutputStream delegate = new FileOutputStream(temp);
                
                    @Override
                    public void close() throws IOException {
                        delegate.close();
                        //if already closed, there should be no exception (see spec Closeable)
                        if (temp.exists()) {
                            Files.move(temp, file);
                        }
                    }
                
                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        delegate.write(b, off, len);
                    }
                
                    @Override
                    public void flush() throws IOException {
                        delegate.flush();
                    }
                
                    @Override
                    public void write(byte[] b) throws IOException {
                        delegate.write(b);
                    }
                
                    @Override
                    public void write(int b) throws IOException {
                        delegate.write(b);
                    }
                };
            } catch (IOException ex)  {
                LOGGER.log(Level.WARNING, "Could not create temporary file {0} writing directly to {1} instead.", 
                        new Object[]{temp, actualFile});
                try {
                    return new FileOutputStream(actualFile);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public File file() {
            if (file.isDirectory()) {
                throw new IllegalStateException("Cannot create file: is already a directory.");
            }
            try {
                if (!file.exists() && 
                        !((file.getParentFile() == null || file.getParentFile().exists() || file.getParentFile().mkdirs())
                                && file.createNewFile())) {
                    throw new IllegalStateException("Could not create file.");
                }
            } catch (IOException e) {
                 throw new IllegalStateException(e);
            }
            return file;
        }

        @Override
        public File dir() {
            if (file.exists() && !file.isDirectory()) {
                throw new IllegalStateException("Cannot create directory: is already a file.");
            }
            if (!file.exists() && !file.mkdirs()) {
                throw new IllegalStateException("Could not create directory.");
            }
            return file;
        }

        @Override
        public long lastmodified() {
            return file.lastModified();
        }

        @Override
        public Resource parent() {
            return new ResourceAdaptor(file.getParentFile());
        }

        @Override
        public Resource get(String resourcePath) {
            return new ResourceAdaptor(new File(file, resourcePath));
        }

        @Override
        public List<Resource> list() {
            if (!file.isDirectory()) {
            	return Collections.emptyList();
            }
            List<Resource> result = new ArrayList<Resource>();
            for (File child : file.listFiles()) {
                result.add(new ResourceAdaptor(child));
            }
            return result;
        }

        @Override
        public Type getType() {
            return file.exists() ? (file.isDirectory()? Type.DIRECTORY : Type.RESOURCE) : Type.UNDEFINED;
        }

        @Override
        public String toString() {
            return "ResourceAdaptor("+file+")";
        }

        @Override
        public boolean delete() {
            return Files.delete(file);
        }

        @Override
        public boolean renameTo(Resource dest) {
            if(dest instanceof FileSystemResourceStore.FileSystemResource) {
                return file.renameTo(((FileSystemResourceStore.FileSystemResource)dest).file);
            } else if(dest instanceof ResourceAdaptor) {
                    return file.renameTo(((ResourceAdaptor)dest).file);
            } else {
                return Resources.renameByCopy(this, dest);
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((file == null) ? 0 : file.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ResourceAdaptor other = (ResourceAdaptor) obj;
            if (file == null) {
                if (other.file != null)
                    return false;
            } else if (!file.equals(other.file))
                return false;
            return true;
        }

    }

    private static final Logger LOGGER = Logging.getLogger(Files.class);
    
    /**
     * Watcher used for {@link #asResource(File)} resources.
     * <p>
     * Each file is monitored for change.
     */
    static final FileSystemWatcher watcher = new FileSystemWatcher();
    
    private Files() {
        // utility class do not subclass
    }
    
    /**
     *
     * @deprecated use {@link Resources#fromURL(Resource, String)}
     */
    @Deprecated 
    public static File url(File baseDirectory, String url) {
        Resource res = Resources.fromURL(asResource(baseDirectory), url);
        if (res == null) {
            return null;
        }
        File file = Resources.find(res);
        if (file == null) {
            return new File(baseDirectory, res.path());
        }
        return file;
    }
    
    /**
     * Adapter allowing a File reference to be quickly used a Resource.
     * 
     * This is used as a placeholder when updating code to use resource, while still maintaining deprecated File methods: 
     * <pre><code>
     * //deprecated
     * public FileWatcher( File file ){
     *    this.resource = Files.asResource( file );
     * }
     * //deprecated
     * public FileWatcher( Resource resource ){
     *    this.resource = resource;    
     * }
     * </code></pre>
     * Note this only an adapter for single files (not directories).
     * 
     * @param file File to adapt as a Resource
     * @return resource adaptor for provided file
     */
    public static Resource asResource(final File file ){
        if( file == null ){
            throw new IllegalArgumentException("File required");
        }
        return new ResourceAdaptor(file);
    }
    
    /**
     * Schedule delay used when tracking {@link #asResource(File)} files.
     * <p>
     * Access provided for test cases.
     */    
    public static void schedule(long delay, TimeUnit unit) {
        watcher.schedule(delay, unit);
    }
    
    /**
     * Safe buffered output stream to temp file, output stream close used to renmae file into place.
     * 
     * @return buffered output stream to temporary file (output stream close used to rename file into place)
     */
    public static OutputStream out(final File file) throws FileNotFoundException {
        // first save to a temp file
        final File temp = new File(file.getParentFile(), file.getName() + ".tmp");

        if (temp.exists()) {
            temp.delete();
        }
        return new OutputStream() {
            FileOutputStream delegate = new FileOutputStream(temp);

            @Override
            public void close() throws IOException {
                delegate.close();
                // no errors, overwrite the original file
                Files.move(temp, file);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                delegate.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                delegate.flush();
            }

            @Override
            public void write(byte[] b) throws IOException {
                delegate.write(b);
            }

            @Override
            public void write(int b) throws IOException {
                delegate.write(b);
            }
        };
    }

    /**
     * Moves (or renames) a file.
     *  
     * @param source The file to rename.
     * @param dest The file to rename to. 
     * @return <code>true</code> if source file moved to dest
     */
    public static boolean move( File source, File dest ) throws IOException {
        if( source == null || !source.exists()){
            throw new NullPointerException("File source required");
        }
        if( dest == null ){
            throw new NullPointerException("File dest required");
        }
        // same path? Do nothing
        if (source.getCanonicalPath().equalsIgnoreCase(dest.getCanonicalPath())){
            return true;
        }

        // windows needs special treatment, we cannot rename onto an existing file
        boolean win = System.getProperty("os.name").startsWith("Windows");
        if ( win && dest.exists() ) {
            // windows does not do atomic renames, and can not rename a file if the dest file
            // exists
            if (!dest.delete()) {
                throw new IOException("Failed to move " + source.getAbsolutePath() + " - unable to remove existing: " + dest.getCanonicalPath());
            }
        }
        // make sure the rename actually succeeds
        if(!source.renameTo(dest)) {
            throw new IOException("Failed to move " + source.getAbsolutePath() + " to " + dest.getAbsolutePath());
        }
        return true;
    }
    
    /**
     * Easy to use file delete (works for both files and directories).
     * 
     * Recursively deletes the contents of the specified directory, 
     * and finally wipes out the directory itself. For each
     * file that cannot be deleted a warning log will be issued.
     * 
     * @param file File to remove
     * @return true if any file present is removed
     */
    public static boolean delete(File file) {
        if( file.isDirectory()){
            emptyDirectory(file);    
        }
        return file.delete();
    }

    /**
     * Recursively deletes the contents of the specified directory 
     * (but not the directory itself). For each
     * file that cannot be deleted a warning log will be issued.
     * 
     * @param dir
     * @throws IOException
     * @returns true if all the directory contents could be deleted, false otherwise
     */
    private static boolean emptyDirectory(File directory) {
        if (!directory.isDirectory()){
            throw new IllegalArgumentException(directory
                    + " does not appear to be a directory at all...");
        }

        boolean allClean = true;
        File[] files = directory.listFiles();

        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                allClean &= delete(files[i]);
            } else {
                if (!files[i].delete()) {
                    LOGGER.log(Level.WARNING, "Could not delete {0}", files[i].getAbsolutePath());
                    allClean = false;
                }
            }
        }
        
        return allClean;
    }
    
}