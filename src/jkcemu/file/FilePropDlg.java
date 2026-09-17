/*
 * (c) 2008-2024 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Dialog zur Anzeige der Eigenschaften einer Datei- bzw. eines Verzeichnises
 */

package jkcemu.file;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.EventObject;
import javax.swing.JButton;
import javax.swing.JLabel;
import jkcemu.Main;
import jkcemu.base.BaseDlg;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.lang.LangUtil;


public class FilePropDlg
		extends BaseDlg
		implements FileVisitor<Path>, Runnable
{
  private Path             path;
  private JButton          btnOK;
  private JLabel           sizeLabel;
  private long             dirSize;
  private volatile boolean threadEnabled;


  public FilePropDlg( Frame parent, Path path ) throws IOException
  {
    super( parent, LangUtil.getText( "common.title.properties" ) );
    this.path          = path;
    this.sizeLabel     = null;
    this.dirSize       = 0;
    this.threadEnabled = true;
    try {

      // Dateiattribute lesen
      UserPrincipal       owner     = null;
      GroupPrincipal      group     = null;
      String              posixPerm = null;
      BasicFileAttributes attrs     = null;
      try {
	PosixFileAttributeView view = Files.getFileAttributeView(
						path,
						PosixFileAttributeView.class,
						LinkOption.NOFOLLOW_LINKS );
	if( view != null ) {
	  PosixFileAttributes pAttrs = view.readAttributes();
	  if( pAttrs != null ) {
	    attrs     = pAttrs;
	    owner     = pAttrs.owner();
	    group     = pAttrs.group();
	    posixPerm = PosixFilePermissions.toString( pAttrs.permissions() );
	  }
	}
      }
      catch( Exception ex ) {}
      if( attrs == null ) {
	attrs = Files.readAttributes(
				path,
				BasicFileAttributes.class,
				LinkOption.NOFOLLOW_LINKS );
      }
      if( owner == null ) {
	try {
	  owner = Files.getOwner( path, LinkOption.NOFOLLOW_LINKS );
	}
	catch( Exception ex ) {}
      }

      // Ausgabeliste aufbereiten
      int                      sizeLineIdx = -1;
      java.util.List<String[]> lines       = new ArrayList<>();
      String                   text        = path.toString();
      Path                     namePath    = path.getFileName();
      if( namePath != null ) {
	String s = namePath.toString();
	if( s != null ) {
	  if( !s.isEmpty() ) {
	    text = s;
	  }
	}
      }
      if( text != null ) {
	if( !text.isEmpty() ) {
	  lines.add( new String[] { LangUtil.getText(
				"file.text.name_colon" ),
			text } );
	}
      }
      if( attrs.isRegularFile() ) {
	lines.add( new String[] { LangUtil.getText( "common.label.type" ),
		LangUtil.getText( "file.text.regular_file" ) } );
	long size = attrs.size();
	if( size >= 0 ) {
	  lines.add( new String[] {
			LangUtil.getText( "common.text.size" ),
			EmuUtil.formatSize( size, false, true ) } );
	}
      }
      else if( attrs.isDirectory() ) {
	lines.add( new String[] { LangUtil.getText( "common.label.type" ),
		LangUtil.getText( "file.text.directory_verzeichnis" ) } );
	sizeLineIdx = lines.size();
	lines.add( new String[] {
			LangUtil.getText( "file.text.size_all_files" ),
			LangUtil.getText( "file.text.being_calculated" ) } );
      }
      else if( attrs.isSymbolicLink() ) {
	try {
	  String s = null;
	  Path   p = Files.readSymbolicLink( path );
	  if( p != null ) {
	    s = p.toString();
	  }
	  lines.add( new String[] {
				LangUtil.getText( "file.text.symbolic_link_symbolischer" ),
				s != null ? s : "" } );
	}
	catch( Exception ex ) {}
      }
      try {
	FileTime t = Files.getLastModifiedTime( path );
	if( t != null ) {
	  lines.add( new String[] {
		LangUtil.getText( "file.text.last_modified_colon" ),
		DateFormat.getDateTimeInstance(
			DateFormat.MEDIUM,
			DateFormat.MEDIUM ).format(
				new java.util.Date( t.toMillis() ) ) } );
	}
      }
      catch( Exception ex ) {}
      if( owner != null ) {
	String s = owner.getName();
	if( s != null ) {
	  if( !s.isEmpty() ) {
	    lines.add( new String[] { LangUtil.getText( "file.text.owner" ),
			s } );
	  }
	}
      }
      if( group != null ) {
	String s = group.getName();
	if( s != null ) {
	  if( !s.isEmpty() ) {
	    lines.add( new String[] { LangUtil.getText( "file.text.group" ),
			s } );
	  }
	}
      }
      if( posixPerm != null ) {
	lines.add( new String[] { "Berechtigungen:", posixPerm } );
      }
      try {
	lines.add( new String[] {
		"Lesezugriff:",
		Files.isReadable( path ) ? "ja" : "nein" } );
      }
      catch( Exception ex ) {}
      try {
	lines.add( new String[] {
		"Schreibzugriff:",
		Files.isWritable( path ) ? "ja" : "nein" } );
      }
      catch( Exception ex ) {}
      try {
	lines.add( new String[] {
		"Versteckt:",
		Files.isHidden( path ) ? "ja" : "nein" } );
      }
      catch( Exception ex ) {}


      // Layout
      setLayout( new GridBagLayout() );

      GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					1, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 0, 5, 0, 5 ),
					0, 0 );


      // Info ueber Datei/Verzeichnis
      addLines( lines, gbc, sizeLineIdx );

      // Info ueber Datentraeger
      try {
	FileStore store = Files.getFileStore( path );
	if( store != null ) {
	  long storeSize = store.getTotalSpace();
	  if( storeSize > 0 ) {
	    lines.clear();

	    JLabel label = GUIFactory.createLabel(
			LangUtil.getText( "file.label.volume" ) );
	    Font   font  = label.getFont();
	    if( font != null ) {
	      label.setFont(
		new Font( font.getName(), Font.BOLD, font.getSize() ) );
	    }
	    gbc.insets.top = 15;
	    gbc.gridwidth  = GridBagConstraints.REMAINDER;
	    gbc.gridx      = 0;
	    add( label, gbc );

	    gbc.insets.top    = 0;
	    gbc.insets.bottom = 0;
	    gbc.gridwidth     = 1;
	    gbc.gridy++;

	    text = store.name();
	    if( text != null ) {
	      if( !text.isEmpty() ) {
		lines.add( new String[] { LangUtil.getText(
				"file.text.name_colon" ),
			text } );
	      }
	    }
	    text = store.type();
	    if( text != null ) {
	      if( !text.isEmpty() ) {
		lines.add(
			new String[] { LangUtil.getText( "common.label.type" ),
			text } );
	      }
	    }
	    lines.add(
		new String[] {
			LangUtil.getText( "common.label.read_only" ),
			store.isReadOnly() ?
				LangUtil.getText( "file.text.yes" ) : LangUtil.getText( "file.text.no" )
		} );
	    lines.add(
		new String[] {
			LangUtil.getText( "common.text.size" ),
			EmuUtil.formatSize( storeSize, false, true )
		} );
	    lines.add(
		new String[] {
			LangUtil.getText( "file.text.free_space" ),
			EmuUtil.formatSize(
					store.getUnallocatedSpace(),
					false,
					true )
		} );
	    addLines( lines, gbc, -1 );
	  }
	}
      }
      catch( Exception ex ) {}

      // Knopf
      this.btnOK = GUIFactory.createButtonOK();
      this.btnOK.addActionListener( this );
      gbc.anchor        = GridBagConstraints.CENTER;
      gbc.insets.top    = 15;
      gbc.insets.bottom = 10;
      gbc.gridwidth     = GridBagConstraints.REMAINDER;
      gbc.gridx         = 0;
      gbc.gridy++;
      add( this.btnOK, gbc );


      // Fenstergroesse und -position
      pack();
      setParentCentered();
      setResizable( false );


      // Verzeichnisgroesse ermitteln
      if( this.threadEnabled && (this.sizeLabel != null) ) {
	(new Thread(
		Main.getThreadGroup(),
		this,
		"JKCEMU directory size calculator" )).start();
      }
    }
    catch( UnsupportedOperationException ex ) {
      throw new IOException(
		LangUtil.getText( "file.error.file_attributes_cannot_read" ) );
    }
  }


	/* --- FileVisistor --- */

  @Override
  public FileVisitResult postVisitDirectory(
				Path        dir,
				IOException ex ) throws IOException
  {
    if( ex != null ) {
      throw ex;
    }
    return this.threadEnabled ?
		FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
  }


  @Override
  public FileVisitResult preVisitDirectory(
				Path                dir,
				BasicFileAttributes attrs )
  {
    return this.threadEnabled ?
		FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
  }


  @Override
  public FileVisitResult visitFile( Path file, BasicFileAttributes attrs )
  {
    if( attrs.isRegularFile() ) {
      long size = attrs.size();
      if( size > 0 ) {
	this.dirSize += size;
      }
    }
    return this.threadEnabled ?
		FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
  }


  @Override
  public FileVisitResult visitFileFailed(
				Path        file,
				IOException ex ) throws IOException
  {
    if( (ex != null)
	&& (Files.isRegularFile( file, LinkOption.NOFOLLOW_LINKS )
		|| Files.isDirectory( file, LinkOption.NOFOLLOW_LINKS )) )
    {
      throw ex;
    }
    return this.threadEnabled ?
		FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
  }


	/* --- Runnable --- */

  @Override
  public void run()
  {
    if( this.threadEnabled && (this.sizeLabel != null) ) {
      String sizeText = "unbekannt";
      try {
	this.dirSize = 0;
	Files.walkFileTree( this.path, this );
	if( this.threadEnabled ) {
	  sizeText = EmuUtil.formatSize( this.dirSize, false, true );
	}
      }
      catch( AccessDeniedException ex ) {
	sizeText = "wegen fehlender Berechtigung nicht ermittelbar";
      }
      catch( Exception ex ) {
	sizeText = "konnte nicht ermittelt werden";
      }
      final String text = sizeText;
      EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    setSizeText( text );
		  }
		} );
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv = false;
    if( e != null ) {
      Object src = e.getSource();
      if( src != null ) {
        if( src == this.btnOK ) {
	  rv = true;
	  doClose();
	}
      }
    }
    return rv;
  }


  @Override
  public boolean doClose()
  {
    boolean rv = super.doClose();
    if( rv ) {
      this.threadEnabled = false;
    }
    return rv;
  }


	/* --- private Methoden --- */

  private void addLines(
			java.util.List<String[]> lines,
			GridBagConstraints       gbc,
			int                      sizeLineIdx )
  {
    int nLines = lines.size();
    for( int i = 0; i < nLines; i++ ) {
      String[] line = lines.get( i );
      if( line.length > 1 ) {
	gbc.gridx = 0;
	add( GUIFactory.createLabel( line[ 0 ] ), gbc );
	gbc.gridx++;
	JLabel label = GUIFactory.createLabel( line[ 1 ] );
	add( label, gbc );
	gbc.gridy++;
	if( i == sizeLineIdx ) {
	  this.sizeLabel = label;
	}
      }
    }
  }


  private void setSizeText( String text )
  {
    if( this.sizeLabel != null ) {
      this.sizeLabel.setText( text );
      pack();
      setParentCentered();
    }
  }
}
