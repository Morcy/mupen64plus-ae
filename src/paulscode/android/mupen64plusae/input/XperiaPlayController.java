package paulscode.android.mupen64plusae.input;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Set;

import paulscode.android.mupen64plusae.Globals;
import paulscode.android.mupen64plusae.NativeMethods;
import paulscode.android.mupen64plusae.persistent.ConfigFile;
import paulscode.android.mupen64plusae.util.Utility;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.FloatMath;
import android.util.Log;

/**
 * The XperiaPlayController class provides a customizable interface with the
 * Xperia Play touchpad.
 *
 * @author: Paul Lamb
 * 
 * http://www.paulscode.com
 * 
 */
public class XperiaPlayController extends AbstractController
{
    // Maximum number of buttons that a touchpad layout can have:
    public static final int MAX_BUTTONS = 30;
    // "Pixel" dimensions of the pad (assuming they're constant):
    public static final int PAD_WIDTH = 966;
    public static final int PAD_HEIGHT = 360;

    public String name = "";
    public String version = "";
    public String about = "";
    public String author = "";

    // Mask colors for normal N64 buttons:
    private int[] maskColors = new int[18];
    // Normal N64 button-states associated with the mask colors:
    private boolean[] buttonPressed = new boolean[18];

    // --Special buttons that behave like hardware buttons--
    // Mask colors for the SDL buttons:
    private int[] SDLButtonMaskColors = new int[MAX_BUTTONS];
    // SDL scancodes associated with the mask colors:
    private int[] SDLButtonCodes = new int[MAX_BUTTONS];
    // SDL button-states associates with the mask colors:
    private boolean[] SDLButtonPressed = new boolean[MAX_BUTTONS];
    //

    private boolean[] mp64pButtons = new boolean[14];
    // Must be the same order as EButton listing in plugin.h! (input-sdl plug-in) 
    private static final int Right  =  0;
    private static final int Left   =  1;
    private static final int Down   =  2;
    private static final int Up     =  3;
    private static final int Start  =  4;
    private static final int Z      =  5;
    private static final int B      =  6;
    private static final int A      =  7;
    private static final int CRight =  8;
    private static final int CLeft  =  9;
    private static final int CDown  = 10;
    private static final int CUp    = 11;
    private static final int R      = 12;
    private static final int L      = 13;
    // Not standard mp64p buttons, but simulated here for better control:
    private static final int UpRight    = 14;
    private static final int RightDown  = 15;
    private static final int LeftDown   = 16;
    private static final int LeftUp     = 17;

    private Image analogMask = null;
    private int analogXpercent = 0;
    private int analogYpercent = 0;
    private int analogPadding = 32;
    private int analogDeadzone = 2;
    private int analogMaximum = 360;
    private int analogPid = -1;

    // All button images and associated mask images, including both 
    // normal N64 buttons and SDL buttons:
    private Image[] masks = new Image[MAX_BUTTONS];
    private int[] xpercents = new int[MAX_BUTTONS];
    private int[] ypercents = new int[MAX_BUTTONS];

    private int buttonCount = 0;  // Total number of buttons
    private int SDLButtonCount = 0;  // Number of SDL buttons

    public Resources resources = null;
    boolean initialized = false;
    public static int whichTouchPad = 0;
    public static int[] touchPadPointerY = new int[256];
    public static int[] touchPadPointerX = new int[256];
    public static boolean[] touchPadPointers = new boolean[256];

    /**
     * Constructor: Instantiates the touch pad layout
     * @param context Handle to the app context.
     * @param attribs Handle to the app resources.
     */
    public XperiaPlayController( Context context, Resources res )
    {
        for( int x = 0; x < 256; x++ )
        {
            XperiaPlayController.touchPadPointers[x] = false;
            XperiaPlayController.touchPadPointerX[x] = -1;
            XperiaPlayController.touchPadPointerY[x] = -1;
            TouchscreenController.touchScreenPointers[x] = false;
            TouchscreenController.touchScreenPointerX[x] = -1;
            TouchscreenController.touchScreenPointerY[x] = -1;
        }
        for( int x = 0; x < 30; x++ )
        {
            TouchscreenController.previousKeyStates[x] = false;
        }
        resources = res;
    }
    
    /**
     * Determines which controls are pressed based on where the pad is being touched.
     * @param pointers Array indicating which pointers are touching the pad.
     * @param pointerX Array containing the X-coordinate of each pointer.
     * @param pointerY Array containing the Y-coordinate of each pointer.
     * @param maxPid Maximum ID of the pointers that have changed (speed optimization)
     */
    public void updatePointers( boolean[] pointers, int[] pointerX, int[] pointerY, int maxPid )
    {
        if( !initialized )
            return;

        int x, y, c, rgb;
        float d, p, dX, dY;
        
        // Clear any previous pointer data:
        int axisX = 0;
        int axisY = 0;
        
        // Clear any data about which buttons were pressed:
        for( int i = 0; i < 18; i++ )
            buttonPressed[i] = false;
        for( int i = 0; i < SDLButtonCount; i++ )
            SDLButtonPressed[i] = false;
        for( int i = 0; i < 14; i++ )
            mp64pButtons[i] = false;

        // Process each pointer in sequence
        for( int i = 0; i <= maxPid; i++ )
        {  
            if( i == analogPid && !pointers[i] )
                analogPid = -1;  // Release analog if it's pointer is not touching the pad
            
            // Pointer is touching the pad
            if( pointers[i] )
            {  
                x = pointerX[i];
                y = pointerY[i];
                
                // Not the analog control, check the buttons
                if( i != analogPid )
                {  
                    for( int m = 0; m < buttonCount; m++ )
                    {  // Check each one in sequence
                        if( x >= masks[m].x && x < masks[m].x + masks[m].width &&
                            y >= masks[m].y && y < masks[m].y + masks[m].height )
                        {  // It is inside this button, check the color mask
                            c = masks[m].image.getPixel( x - masks[m].x, y - masks[m].y );
                            rgb = (int)(c & 0x00ffffff);  // Ignore the alpha component if any
                            if( rgb > 0 )  // Ignore black
                                pressColor( rgb );  // Determine what was pressed
                        }
                    }
                }
               
                if( analogMask != null )
                {
                    dX = (float)( x - (analogMask.x + analogMask.hWidth) );   // Distance from center along x-axis
                    dY = (float)( (analogMask.y + analogMask.hHeight) - y );  // Distance from center along y-axis
                    d = FloatMath.sqrt( (dX * dX) + (dY * dY) );  // Distance from center
                    
                    // Inside the analog control
                    if( (i == analogPid) || (d >= analogDeadzone && d < analogMaximum + analogPadding) )
                    {  
                        // Emulate the analog control as an octagon (like the real N64 controller)
                        if( Globals.userPrefs.touchscreenOctagonJoystick )
                        {  
                            Point crossPt = new Point();
                            float dC = analogMask.hWidth;
                            float dA = FloatMath.sqrt( (dC * dC) / 2.0f );
                        
                            if( dX > 0 && dY > 0 )  // Quadrant I
                            {
                                if( segsCross( 0, 0, dX, dY, 0, dC, dA, dA, crossPt ) ||
                                    segsCross( 0, 0, dX, dY, dA, dA, 80, 0, crossPt ) )
                                {
                                    dX = crossPt.x;
                                    dY = crossPt.y;
                                }
                            }
                            else if( dX < 0 && dY > 0 )  // Quadrant II
                            {
                                if( segsCross( 0, 0, dX, dY, 0, dC, -dA, dA, crossPt ) ||
                                    segsCross( 0, 0, dX, dY, -dA, dA, -dC, 0, crossPt ) )
                                {
                                    dX = crossPt.x;
                                    dY = crossPt.y;
                                }
                            }
                            else if( dX < 0 && dY < 0 )  // Quadrant III
                            {
                                if( segsCross( 0, 0, dX, dY, -dC, 0, -dA, -dA, crossPt ) ||
                                    segsCross( 0, 0, dX, dY, -dA, -dA, 0, -dC, crossPt ) )
                                {
                                    dX = crossPt.x;
                                    dY = crossPt.y;
                                }
                            }
                            else if( dX > 0 && dY < 0 )  // Quadrant IV
                            {
                                if( segsCross( 0, 0, dX, dY, 0, -dC, dA, -dA, crossPt ) ||
                                    segsCross( 0, 0, dX, dY, dA, -dA, dC, 0, crossPt ) )
                                {
                                    dX = crossPt.x;
                                    dY = crossPt.y;
                                }
                            }
                            d = FloatMath.sqrt( (dX * dX) + (dY * dY) );  // distance from center
                        }
                        analogPid = i;  // "Capture" the analog control
                        p = (d - (float)analogDeadzone) / (float)(analogMaximum - analogDeadzone);  // percentage of full-throttle
                        if( p < 0 )
                            p = 0;
                        if( p > 1 )
                            p = 1;
                        
                        // From the N64 function ref: The 3D Stick data is of type signed char and in
                        // the range between 80 and -80. (32768 / 409 = ~80.1)
                        axisX = (int) ( (dX / d) * p * 80.0f );
                        axisY = (int) ( (dY / d) * p * 80.0f );
                        if( axisX > 80 )
                            axisX = 80;
                        if( axisX < -80 )
                            axisX = -80;
                        if( axisY > 80 )
                            axisY = 80;
                        if( axisY < -80 )
                            axisY = -80;
                    }
                }
            }
        }
        NativeMethods.updateVirtualGamePadStates( 0, mp64pButtons, axisX, axisY );
        TouchscreenController.updateSDLButtonStates( SDLButtonPressed, SDLButtonCodes, SDLButtonCount );
    }

    /**
     * Determines which button was pressed based on the closest mask color.
     * TODO: Android is not precise: the color is different than it should be!)
     * @param color Color of the pixel that the user pressed.
     */
    protected void pressColor( int color )
    {
        int closestMatch = 0;  // Start with the first N64 button
        int closestSDLButtonMatch = -1;  // Disable this to start with
        int matchDif = Math.abs( maskColors[0] - color );
        int dif;
        
        for( int x = 1; x < 18; x++ )
        {  // Go through the N64 button mask colors first
            dif = Math.abs( maskColors[x] - color );
            if( dif < matchDif )
            {  // This is a closer match
                closestMatch = x;
                matchDif = dif;
            }
        }
        
        for( int x = 0; x < SDLButtonCount; x++ )
        {  // Now see if any of the SDL button mask colors are closer
            dif = Math.abs( SDLButtonMaskColors[x] - color );
            if( dif < matchDif )
            {  // This is a closer match
                closestSDLButtonMatch = x;
                matchDif = dif;
            }
        }

        if( closestSDLButtonMatch > -1 )
        {  
            // Found an SDL button that matches the color
            SDLButtonPressed[closestSDLButtonMatch] = true;
        }
        else
        {  
            // One of the N64 buttons matched the color
            buttonPressed[closestMatch] = true;
           
            // Only 14 buttons in Mupen64Plus API
            if( closestMatch < 14 )
            {
                mp64pButtons[closestMatch] = true;
            }
            // Simulate the remaining buttons:
            else if( closestMatch == UpRight )
            {
                mp64pButtons[Up] = true;
                mp64pButtons[Right] = true;
            }
            else if( closestMatch == RightDown )
            {
                mp64pButtons[Right] = true;
                mp64pButtons[Down] = true;
            }
            else if( closestMatch == LeftDown )
            {
                mp64pButtons[Left] = true;
                mp64pButtons[Down] = true;
            }
            else if( closestMatch == LeftUp )
            {
                mp64pButtons[Left] = true;
                mp64pButtons[Up] = true;
            }
        }
    }
    
    public void loadPad()
    {
        // TODO: Encapsulate call to overloaded method
//        if( !Globals.user.touchscreenEnabled )
//            loadPad( null );
//        else if( !Globals.user.touchscreenLayoutIndex.isEmpty() )
//            loadPad( Globals.user.touchscreenLayoutIndex );
//        else if( Globals.Game.mTouchPadListing.numPads > 0 )
//            loadPad( mTouchPadListing.padNames[0] );
//        else
//        {
//            loadPad( null );
//            Log.v( "GameActivityXperiaPlay", "No touchpad skins found" );
//        }
    }

    /**
     * Loads the specified touchpad skin
     * @param skin Name of the layout skin to load.
     */
    protected void loadPad( String skin )
    {
        initialized = false;  // Stop anything accessing settings while loading
        // Clear everything out to be re-populated with the new settings:
        name = "";
        version = "";
        about = "";
        author = "";
        analogMask = null;
        analogXpercent = 0;
        analogYpercent = 0;
        masks = new Image[MAX_BUTTONS];
        xpercents = new int[MAX_BUTTONS];
        ypercents = new int[MAX_BUTTONS];
        buttonCount = 0;
        SDLButtonCount = 0;
        String filename;

        for( int i = 0; i < 18; i++ )
        {
            maskColors[i] = -1;
            buttonPressed[i] = false;
        }
        for( int i = 0; i < MAX_BUTTONS; i++ )
        {
            SDLButtonMaskColors[i] = -1;
            SDLButtonCodes[i] = -1;
            SDLButtonPressed[i] = false;
        }
        for( int i = 0; i < 14; i++ )
            mp64pButtons[i] = false;

        if( skin == null )
            return;  // No skin was specified, so we are done.. quit
        // Load the configuration file (pad.ini):
        ConfigFile pad_ini = new ConfigFile( Globals.path.dataDir + "/skins/touchpads/" + skin + "/pad.ini" );

        // Look up the touch-pad layout credits:
        name = pad_ini.get( "INFO", "name" );
        version = pad_ini.get( "INFO", "version" );
        about = pad_ini.get( "INFO", "about" );
        author = pad_ini.get( "INFO", "author" );

        Set<String> keys;
        Iterator<String> iter;
        String param, val;
        int valI;
        // Look up the mask colors:
        ConfigFile.ConfigSection section = pad_ini.get( "MASK_COLOR" );
        if( section != null )
        {
            keys = section.keySet();
            iter = keys.iterator();
            while( iter.hasNext() )
            {   // Loop through the param=val pairs
                param = iter.next();
                val = section.get( param );
                valI = Utility.toInt( val, -1 ); // -1 (undefined) in case of number format problem
                param = param.toLowerCase();  // lets not make this part case-sensitive
                if( param.equals( "cup" ) )
                    maskColors[CUp] = valI;
                else if( param.equals( "cright" ) )
                    maskColors[CRight] = valI;
                else if( param.equals( "cdown" ) )
                    maskColors[CDown] = valI;
                else if( param.equals( "cleft" ) )
                    maskColors[CLeft] = valI;
                else if( param.equals( "a" ) )
                    maskColors[A] = valI;
                else if( param.equals( "b" ) )
                    maskColors[B] = valI;
                else if( param.equals( "l" ) )
                    maskColors[L] = valI;
                else if( param.equals( "r" ) )
                    maskColors[R] = valI;
                else if( param.equals( "z" ) )
                    maskColors[Z] = valI;
                else if( param.equals( "start" ) )
                    maskColors[Start] = valI;
                else if( param.equals( "leftup" ) )
                    maskColors[LeftUp] = valI;
                else if( param.equals( "up" ) )
                    maskColors[Up] = valI;
                else if( param.equals( "upright" ) )
                    maskColors[UpRight] = valI;
                else if( param.equals( "right" ) )
                    maskColors[Right] = valI;
                else if( param.equals( "rightdown" ) )
                    maskColors[RightDown] = valI;
                else if( param.equals( "leftdown" ) )
                    maskColors[LeftDown] = valI;
                else if( param.equals( "down" ) )
                    maskColors[Down] = valI;
                else if( param.equals( "left" ) )
                    maskColors[Left] = valI;
                else if( param.contains( "scancode_" ) )
                {
                    try
                    {  // Make sure a valid integer was used for the scancode
                        SDLButtonCodes[SDLButtonCount] = Integer.valueOf( param.substring( 9, param.length() ) );
                        SDLButtonMaskColors[SDLButtonCount] = valI;
                        SDLButtonCount++;
                    }
                    catch( NumberFormatException nfe )
                    {}  // Skip it if this happens
                }
            }
        }
       
        Set<String> mKeys = pad_ini.keySet();
        // Loop through all the sections
        for ( String mKey : mKeys )
        {
            filename = mKey;  // The rest of the sections are filenames
            if ( filename != null && filename.length() > 0 &&
                    !filename.equals( "INFO" ) && !filename.equals( "MASK_COLOR" ) &&
                    !filename.equals( "[<sectionless!>]" ) )
            {  // Yep, its definitely a filename
                section = pad_ini.get( filename );
                if ( section != null )
                {  // Process the parameters for this section
                    val = section.get("info");  // what type of control
                    if ( val != null )
                    {
                        val = val.toLowerCase();  // Lets not make this part case-sensitive
                        if (val.contains( "analog" ) )
                        {  // Analog color mask image in BMP image format (doesn't actually get drawn)
                            analogMask = new Image( resources, Globals.path.dataDir + "/skins/touchpads/" +
                                                     skin + "/" + filename + ".bmp" );
                            // Position (percentages of the screen dimensions):
                            analogXpercent = Utility.toInt( section.get( "x" ), 0 );
                            analogYpercent = Utility.toInt( section.get( "y" ), 0 );
                            // Sensitivity (percentages of the radius, i.e. half the image width):
                            analogDeadzone = (int) ( (float) analogMask.hWidth *
                                                             ( Utility.toFloat( section.get( "min" ), 1 ) / 100.0f ) );
                            analogMaximum = (int) ( (float) analogMask.hWidth *
                                                            ( Utility.toFloat( section.get( "max" ), 55 ) / 100.0f ) );
                            analogPadding = (int) ( (float) analogMask.hWidth *
                                                            ( Utility.toFloat( section.get( "buff" ), 55 ) / 100.0f ) );
                            analogMask.fitCenter( (int) ( (float) PAD_WIDTH * ((float) analogXpercent / 100.0f) ),
                                                  (int) ( (float) PAD_HEIGHT * ((float) analogYpercent / 100.0f) ),
                                                  PAD_WIDTH, PAD_HEIGHT );
                        }
                        else
                        {   // A button control (may contain one or more N64 buttons and/or SDL buttons)
                            // Button color mask image in BMP image format (doesn't actually get drawn)
                            masks[buttonCount] = new Image( resources, Globals.path.dataDir + "/skins/touchpads/" +
                                                            skin + "/" + filename + ".bmp" );
                            // Position (percentages of the screen dimensions):
                            xpercents[buttonCount] = Utility.toInt( section.get( "x" ), 0 );
                            ypercents[buttonCount] = Utility.toInt( section.get( "y" ), 0 );
                            masks[buttonCount].fitCenter( (int) ( (float) PAD_WIDTH * ((float) xpercents[buttonCount] / 100.0f) ),
                                                          (int) ( (float) PAD_HEIGHT * ((float) ypercents[buttonCount] / 100.0f) ),
                                                          PAD_WIDTH, PAD_HEIGHT );

                            Log.v( "TouchPad", "Adding button grouping " + buttonCount + ", (" + xpercents[buttonCount] + ", " + ypercents[buttonCount] + ")" );
                            Log.v( "TouchPad", "Fit x center to " + (int) ( (float) PAD_WIDTH * ((float) xpercents[buttonCount] / 100.0f) ) );
                            Log.v( "TouchPad", "Fit y center to " + (int) ( (float) PAD_HEIGHT * ((float) ypercents[buttonCount] / 100.0f) ) );
                            Log.v( "TouchPad", "Converted max coordinates: (" + masks[buttonCount].x + ", " + masks[buttonCount].y + ")" );

                            buttonCount++;
                        }
                    }
                }
            }
        }
        // Free the data that was loaded from the config file:
        pad_ini.clear();
        pad_ini = null;
        
        initialized = true;  // everything is loaded now
    }
    
    public void touchPadBeginEvent()
    {
    }

    public void touchPadPointerDown( int pointer_id )
    {
        XperiaPlayController.touchPadPointers[pointer_id] = true;
    }

    public void touchPadPointerUp( int pointer_id )
    {
        XperiaPlayController.touchPadPointers[pointer_id] = false;
        XperiaPlayController.touchPadPointerX[pointer_id] = -1;
        XperiaPlayController.touchPadPointerY[pointer_id] = -1;
    }

    public void touchPadPointerPosition( int pointer_id, int x, int y )
    {
        XperiaPlayController.touchPadPointers[pointer_id] = true;
        XperiaPlayController.touchPadPointerX[pointer_id] = x;
        XperiaPlayController.touchPadPointerY[pointer_id] = XperiaPlayController.PAD_HEIGHT - y;
        
        // the Xperia Play's touchpad y-axis is flipped for some reason
    }

    public void touchPadEndEvent()
    {
        if( Globals.surfaceInstance != null )
        {
            Globals.surfaceInstance.onTouchPad( XperiaPlayController.touchPadPointers, XperiaPlayController.touchPadPointerX,
                    XperiaPlayController.touchPadPointerY, 64 );
        }
    }

    public void touchScreenBeginEvent()
    {
    }

    public void touchScreenPointerDown( int pointer_id )
    {
        TouchscreenController.touchScreenPointers[pointer_id] = true;
    }

    public void touchScreenPointerUp( int pointer_id )
    {
        TouchscreenController.touchScreenPointers[pointer_id] = false;
        TouchscreenController.touchScreenPointerX[pointer_id] = -1;
        TouchscreenController.touchScreenPointerY[pointer_id] = -1;
    }

    public void touchScreenPointerPosition( int pointer_id, int x, int y )
    {
        TouchscreenController.touchScreenPointers[pointer_id] = true;
        TouchscreenController.touchScreenPointerX[pointer_id] = x;
        TouchscreenController.touchScreenPointerY[pointer_id] = y;
    }

    public void touchScreenEndEvent()
    {
        if( Globals.surfaceInstance != null )
            Globals.surfaceInstance.onTouchScreen( TouchscreenController.touchScreenPointers, TouchscreenController.touchScreenPointerX,
                    TouchscreenController.touchScreenPointerY, 64 );
    }

    public boolean onNativeKey( int action, int keycode )
    {
        if( Globals.surfaceInstance == null )
            return false;
        return Globals.surfaceInstance.onKey( keycode, action );
    }

    /**
     * Determines if the two specified line segments intersect with each other, and calculates
     * where the intersection occurs if they do.
     * @param seg1pt1_x X-coordinate for the first end of the first line segment.
     * @param seg1pt1_y Y-coordinate for the first end of the first line segment.
     * @param seg1pt2_x X-coordinate for the second end of the first line segment.
     * @param seg1pt2_y Y-coordinate for the second end of the first line segment.
     * @param seg2pt1_x X-coordinate for the first end of the second line segment.
     * @param seg2pt1_y Y-coordinate for the first end of the second line segment.
     * @param seg2pt2_x X-coordinate for the second end of the second line segment.
     * @param seg2pt2_y Y-coordinate for the second end of the second line segment.
     * @param crossPt Changed to the point of intersection if there is one, otherwise unchanged.
     * @return True if the two line segments intersect.
     */
    public static boolean segsCross( float seg1pt1_x, float seg1pt1_y, float seg1pt2_x, float seg1pt2_y,
                                     float seg2pt1_x, float seg2pt1_y, float seg2pt2_x, float seg2pt2_y,
                                     Point crossPt )
    {
        float vec1_x = seg1pt2_x - seg1pt1_x;
        float vec1_y = seg1pt2_y - seg1pt1_y;
        
        float vec2_x = seg2pt2_x - seg2pt1_x;
        float vec2_y = seg2pt2_y - seg2pt1_y;
        
        float div = (-vec2_x * vec1_y + vec1_x * vec2_y );
        if( div == 0 )
            return false;  // Segments don't cross
        
        float s = (-vec1_y * (seg1pt1_x - seg2pt1_x) + vec1_x * (seg1pt1_y - seg2pt1_y) ) / div;
        float t = ( vec2_x * (seg1pt1_y - seg2pt1_y) - vec2_y * (seg1pt1_x - seg2pt1_x) ) / div;
        
        if( s >= 0 && s < 1 && t >= 0 && t <= 1 )
        {
            crossPt.x = seg1pt1_x + (t * vec1_x);
            crossPt.y = seg1pt1_y + (t * vec1_y);
            return true;  // Segments cross, point of intersection stored in 'crossPt'
        }
        
        return false;  // Segments don't cross
    }

    /**
     * The Point class is a basic interface for storing 2D float coordinates.
     */
    private static class Point
    {
        public float x;
        public float y;
        /**
         * Constructor: Creates a new point at the origin
         */
        public Point()
        {
            x = 0;
            y = 0;
        }
    }

    /**
     * The TouchPadListing class reads in the listing of touchpads from touchpad_list.ini.
     */
    public static class TouchPadListing
    {
        public int numPads = 0;
        public String[] padNames = new String[256];

        /**
         * Constructor: Reads in the list of touchpads
         * @param filename File containing the list of touchpads (typically touchpad_list.ini).
         */
        public TouchPadListing( String filename )
        {
            try
            {
                FileInputStream fstream = new FileInputStream( filename );
                DataInputStream in = new DataInputStream( fstream );
                BufferedReader br = new BufferedReader( new InputStreamReader( in ) );
                String strLine;
                while( ( strLine = br.readLine() ) != null )
                {
                    if( strLine.length() > 0 )
                    {
                        padNames[numPads] = strLine;
                        numPads++;
                    }
                }
                in.close();
            }
            catch( Exception e )
            {
                Log.e( "TouchPad.TouchPadListing", "Exception, error message: " + e.getMessage() );
            }
        }
    }

    /**
     * The Image class provides a simple interface to common image manipulation methods.
     */
    private class Image
    {
        public BitmapDrawable drawable = null;
        public Bitmap image = null;
        public Rect drawRect = null;

        public int x = 0;
        public int y = 0;
        public int width = 0;
        public int height = 0;
        public int hWidth = 0;
        public int hHeight = 0;

        /**
         * Constructor: Loads an image file and sets the initial properties.
         * @param res Handle to the app resources.
         * @param filename Path to the image file.
         */
        public Image( Resources res, String filename )
        {
            image = BitmapFactory.decodeFile( filename );
            drawable = new BitmapDrawable( res, image );
            if( image != null )
                width = image.getWidth();
            hWidth = (int) ((float)width / 2.0f);
            if( image != null )
                height = image.getHeight();
            hHeight = (int) ((float)height / 2.0f);
            drawRect = new Rect();
        }
        
        /**
         * Constructor: Creates a clone copy of another Image.
         * @param res Handle to the app resources.
         * @param clone Image to copy.
         */
        public Image( Resources res, Image clone )
        {
            image = clone.image;
            drawable = new BitmapDrawable( res, image );
            width = clone.width;
            hWidth = clone.hWidth;
            height = clone.height;
            hHeight = clone.hHeight;
            drawRect = new Rect();
        }
        
        /**
         * Sets the screen position of the image (in pixels).
         * @param x X-coordinate.
         * @param y Y-coordinate.
         */
        public void setPos( int x, int y )
        {
            this.x = x;
            this.y = y;
            drawRect.set( x, y, x + width, y + height );
            drawable.setBounds( drawRect );
        }
        
        /**
         * Centers the image at the specified coordinates, without going beyond the
         * specified screen dimensions.
         * @param centerX X-coordinate to center the image at.
         * @param centerY Y-coordinate to center the image at.
         * @param screenW Horizontal screen dimension (in pixels).
         * @param centerY Vertical screen dimension (in pixels).
         */
        public void fitCenter( int centerX, int centerY, int screenW, int screenH )
        {
            int cx = centerX;
            int cy = centerY;
            if( cx < hWidth )
                cx = hWidth;
            if( cy < hHeight )
                cy = hHeight;
            if( cx + hWidth > screenW )
                cx = screenW - hWidth;
            if( cy + hHeight > screenH )
                cy = screenH - hHeight;
            x = cx - hWidth;
            y = cy - hHeight;
            drawRect.set( x, y, x + width, y + height );
            drawable.setBounds( drawRect );
        }
        
        /**
         * Centers the image at the specified coordinates, without going beyond the
         * edges of the specified rectangle.
         * @param centerX X-coordinate to center the image at.
         * @param centerY Y-coordinate to center the image at.
         * @param rectX X-coordinate of the bounding rectangle.
         * @param rectY Y-coordinate of the bounding rectangle.
         * @param rectW Horizontal bounding rectangle dimension (in pixels).
         * @param rectH Vertical bounding rectangle dimension (in pixels).
         */
        public void fitCenter( int centerX, int centerY, int rectX, int rectY, int rectW, int rectH )
        {
            int cx = centerX;
            int cy = centerY;
            if( cx < rectX + hWidth )
                cx = rectX + hWidth;
            if( cy < rectY + hHeight )
                cy = rectY + hHeight;
            if( cx + hWidth > rectX + rectW )
                cx = rectX + rectW - hWidth;
            if( cy + hHeight > rectY + rectH )
                cy = rectY + rectH - hHeight;
            x = cx - hWidth;
            y = cy - hHeight;
            drawRect.set( x, y, x + width, y + height );
            drawable.setBounds( drawRect );
        }
        
        /**
         * Draws the image.
         * @param canvas Canvas to draw the image on.
         */
        public void draw( Canvas canvas )
        {
            drawable.draw( canvas );
        }
    }
}