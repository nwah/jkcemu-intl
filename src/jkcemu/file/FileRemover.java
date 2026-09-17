/*
 * (c) 2014-2020 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Loeschen von Dateien und Dateibaeumen
 */

package jkcemu.file;

import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JCheckBox;
import jkcemu.base.BaseDlg;
import jkcemu.base.DesktopHelper;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.lang.LangUtil;


public class FileRemover extends AbstractFileWorker
{
  public static void startRemove(
			Window                         owner,
			java.util.List<Path>           paths,
			PathListener                   pathListener,
			Collection<AbstractFileWorker> register )
  {
    if( paths != null ) {
      int n = paths.size();
      if( n > 0 ) {
	StringBuilder buf = new StringBuilder( 128 );
	if( n == 1 ) {
	  Path p = paths.get( 0 );
	  if( Files.isDirectory( p ) ) {
	    buf.append( LangUtil.getText(
		"file.text.want_delete_directory",
		p ) );
	  } else if( Files.isSymbolicLink( p ) ) {
	    buf.append( LangUtil.getText(
		"file.text.want_delete_symbolic",
		p ) );
	  } else {
	    buf.append( LangUtil.getText(
		"file.text.want_delete_file",
		p ) );
	  }
	} else {
	  int nDirs  = 0;
	  int nFiles = 0;
	  int nLinks = 0;
	  for( Path p : paths ) {
	    if( Files.isDirectory( p ) ) {
	      nDirs++;
	    } else if( Files.isSymbolicLink( p ) ) {
	      nLinks++;
	    } else {
	      nFiles++;
	    }
	  }
	  /*
	   * Die Aufzaehlung wird aus einzeln uebersetzbaren
	   * Teilen zusammengesetzt, damit die Uebersetzung
	   * die jeweils passende Form waehlen kann.
	   */
	  java.util.List<String> parts = new ArrayList<>();
	  if( nDirs == 1 ) {
	    parts.add( LangUtil.getText( "file.text.directory_das" ) );
	  } else if( nDirs > 1 ) {
	    parts.add( LangUtil.getText( "file.text.directories", nDirs ) );
	  }
	  if( nFiles == 1 ) {
	    parts.add( LangUtil.getText( "file.text.file" ) );
	  } else if( nFiles > 1 ) {
	    parts.add( LangUtil.getText( "file.text.files", nFiles ) );
	  }
	  if( nLinks == 1 ) {
	    parts.add( LangUtil.getText( "file.text.symbolic_link_den" ) );
	  } else if( nLinks > 1 ) {
	    parts.add( LangUtil.getText(
				"file.text.symbolic_links", nLinks ) );
	  }
	  StringBuilder itemBuf = new StringBuilder( 64 );
	  for( int i = 0; i < parts.size(); i++ ) {
	    if( i > 0 ) {
	      itemBuf.append( i == (parts.size() - 1) ?
				LangUtil.getText( "file.text.and" )
				: LangUtil.getText( ", " ) );
	    }
	    itemBuf.append( parts.get( i ) );
	  }
	  buf.append( LangUtil.getText(
		"file.text.want_delete",
		itemBuf.toString() ) );
	}

	boolean status      = false;
	boolean moveToTrash = false;
	if( DesktopHelper.isMoveToTrashSupported() ) {
	  JCheckBox cb = GUIFactory.createCheckBox(
					LangUtil.getText(
						"file.option.move_trash" ),
					true );
	  status      = BaseDlg.showYesNoDlg( owner, buf.toString(), cb );
	  moveToTrash = cb.isSelected();
	} else {
	  status = BaseDlg.showYesNoDlg( owner, buf.toString() );
	}
	if( status ) {
	  if( moveToTrash ) {
	    try {
	      Set<Path> removedPaths = new HashSet<>();
	      try {
		for( Path path : paths ) {
		  DesktopHelper.moveToTrash( path.toFile() );
		  removedPaths.add( path );
		}
	      }
	      finally {
		if( !removedPaths.isEmpty() ) {
		  pathListener.pathsRemoved( removedPaths );
		}
	      }
	    }
	    catch( IOException ex ) {
	      BaseDlg.showErrorDlg( owner, ex );
	    }
	  } else {
	    (new FileRemover(
			owner,
			paths,
			moveToTrash,
			pathListener,
			register )).startWork();
	  }
	}
      }
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public String getFileFailedMsg( String fileName )
  {
    return LangUtil.getText( "file.text.cannot_deleted",
		fileName );
  }


  @Override
  public String getProgressDlgTitle()
  {
    return LangUtil.getText( EmuUtil.TEXT_DELETE );
  }


  @Override
  public String getUncompletedWorkMsg()
  {
    return LangUtil.getText( "file.text.not_all_files_directories_symbolic_links_deleted" );
  }


  @Override
  public FileVisitResult postVisitDirectory( Path dir, IOException ex )
  {
    return delete( dir );
  }


  @Override
  public FileVisitResult visitFile( Path file, BasicFileAttributes attrs )
  {
    return delete( file );
  }


	/* --- Konstruktor --- */

  private FileRemover(
		Window                         owner,
		java.util.List<Path>           paths,
		boolean                        moveToTrash,
		PathListener                   pathListener,
		Collection<AbstractFileWorker> register )
  {
    super( owner, paths, pathListener, register );
  }


	/* --- private Methoden --- */

  private FileVisitResult delete( Path path )
  {
    boolean done = false;
    while( !done && !this.cancelled ) {
      done         = true;
      this.curPath = path;
      try {
	Files.delete( path );
	pathRemoved( path );
      }
      catch( NoSuchFileException ex1 ) {}
      catch( IOException ex1 ) {
	done = !handleError( path, ex1, true );
      }
    }
    return this.cancelled ?
		FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
  }
}
