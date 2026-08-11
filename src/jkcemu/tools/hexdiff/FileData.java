/*
 * (c) 2008-2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Daten einer Datei
 */

package jkcemu.tools.hexdiff;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import jkcemu.lang.LangUtil;


public class FileData implements Closeable
{
  private File        file;
  private InputStream in;


  public FileData( File file ) throws IOException
  {
    if( !file.exists() ) {
      throw new IOException( LangUtil.tr(
		"{0}:\nDatei nicht gefunden", file.getPath() ) );
    }
    if( !file.isFile() ) {
      throw new IOException( LangUtil.tr(
		"{0}:\nKeine regul\u00E4re Datei", file.getPath() ) );
    }
    if( !file.canRead() ) {
      throw new IOException( LangUtil.tr(
		"{0}:\nDatei nicht lesbar", file.getPath() ) );
    }
    this.file = file;
    this.in   = null;
  }


  public File getFile()
  {
    return this.file;
  }


  public int read() throws IOException
  {
    if( this.in == null ) {
      this.in = new BufferedInputStream( new FileInputStream( this.file ) );
    }
    return this.in.read();
  }


	/* --- Closeable --- */

  @Override
  public void close() throws IOException
  {
    if( this.in != null ) {
      this.in.close();
      this.in = null;
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public String toString()
  {
    return this.file.getPath();
  }
}
