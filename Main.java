/**
 * COMP1008 Coursework
 * 
 * 
 * 
 * @author Emily Shepherd
 * @ecsId  ams2g11
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageFilter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;


/**
 * Main
 * 
 * Starts the system.
 * 
 * @author Emily Shepherd
 */
public class Main
{
	/**
	 * main
	 * 
	 * Starts the program by starting a new
	 * MandelbrotUI JFrame.
	 * 
	 * @param args
	 * @see MandelbrotUI
	 */
	public static void main(String[] args)
	{
		new MandelbrotUI();
	}
}


/**
 * AbstractUI
 * 
 * Mandelbrot and Julia UI Frames should extend this
 * 
 * Sets up common methods, like painting the
 * BufferedImage to the UIPane, and recalculating
 * the fractal on resize
 * 
 * @author Emily Shepherd
 *
 */
abstract class AbstractUI extends JFrame implements ComponentListener, AlgorithmFinishedListener
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * The JPanel the fractal will be drawn to
     */
    protected JPanel UIPane = new JPanel();
    
    /**
     * This object will do our drawing
     */
    protected AlgorithmChecker ac;
    
    /**
     * The point the user clicked on
     */
    protected ComplexNumber userSelectedPoint;
    
    /**
     * This is the image given to the Frame by the
     * AlgorithmChecker when it has finished drawing
     * the fractal
     */
    protected BufferedImage image;
    
    /**
     * AbstractUI
     * 
     * Sets up the Frame
     * 
     * @param title The JFrame title
     */
    public AbstractUI(String title)
    {
        super(title);
        
        //For resizing listening
        this.addComponentListener(this);
        
        UIPane = new JPanel()
        {
            /**
             * 
             */
            private static final long serialVersionUID = 1L;

            /**
             * paintComponent
             * 
             * If a fractal image is available, this one
             * draw it. Otherwise, it will ask ac to calculate
             * one (safe to call from this thread as ac.start()
             * does no calculations, it starts its child threads)
             */
            public void paintComponent(Graphics painter)
            {
                //Java sometimes randomly called paint() before the
                //ac was ready. If we get here and we don't have an
                //image, its acceptable to ask the AlgorithmChecker
                //to build us one, as obviously someone want GUI
                if (image != null)
                {
                    painter.drawImage(image, 0, 0, UIPane.getWidth(), UIPane.getHeight(), null);
                }
                else
                {
                    //Safe to call from the EDT, as this method does
                    //no calculations, it just sets off the child
                    //threads
                    ac.start();
                }
            }
        };
    }
    
    /**
     * finishedImage
     * 
     * Callback for AlgorithmChecker when the fractal
     * has been calculated. This method will repaint
     * the frame immediately.
     * 
     * @see AlgorithmFinishedListener.finishedImage
     */
    public void finishedImage(BufferedImage image)
    {
        this.image = image;
        repaint();
    }
    
    /**
     * componentResized
     * 
     * Recalculates the fractal
     */
    public void componentResized(ComponentEvent e)
    {
        ac.resize(UIPane.getWidth(), UIPane.getHeight());
        ac.start();
    }

    /**
     * Unused
     */
    public void componentHidden(ComponentEvent e){}
    public void componentMoved(ComponentEvent e){}
    public void componentShown(ComponentEvent e){}
}

/**
 * AlgorthimFinishedListener
 * 
 * Classes that implement this interface can handle
 * AlgorithmChecker's finishedImage event
 * 
 * @author Emily Shepherd
 *
 */
interface AlgorithmFinishedListener
{
    /**
     * finishedDrawing
     * 
     * Called when the AlgorithmChecker has finished
     * calculating the fractal
     * 
     * @param image The fractal
     */
    public void finishedImage(BufferedImage image);
}

/**
 * AlgorithmChecker
 * 
 * This class controls the threads that calculate
 * our fractals.
 * 
 * CalculatorThread is a nested class within this,
 * these do the work.
 * 
 * This class creates a thread per processor on the
 * machine in the hope that the OS will run them
 * simultaneously. Each thread takes responsibility
 * for drawing a stripe of the fractal. These
 * stripes run along the y axis, that is. Each thread
 * draws the every y value for its x axis bounds.
 * 
 * EG: For a quad-core processor, the image would be
 * drawn be 4 threads, and striped like so:
 *   -------------------------
 *   |     |     |     |     |
 *   |     |     |     |     |
 *   |  0  |  1  |  2  |  3  |
 *   |     |     |     |     |
 *   |     |     |     |     |
 *   -------------------------
 * 
 * @author Emily Shepherd
 *
 */
abstract class AlgorithmChecker
{
    /**
     * Number of iterations we should perform
     */
    protected int iterations = 100;
    
    /**
     * threads contains all our threads
     * running holds only running threads
     */
    private ArrayList<CalculatorThread> threads =
            new ArrayList<CalculatorThread>();
    private ArrayList<CalculatorThread> running =
            new ArrayList<CalculatorThread>();
    
    /**
     * These will have their finishedImage() method
     * called when the image has been calculated
     * 
     * @see AlgorithmFinishedListener.finishedImage
     */
    private ArrayList<AlgorithmFinishedListener> listeners =
        new ArrayList<AlgorithmFinishedListener>();
    
    /**
     * The image of the fractal
     */
    private BufferedImage image;
    
    /**
     * This value is used to scale the color
     * It is calculated from the number of interations
     * and is set when setIterations() is called
     * 
     * @see setIterations
     */
    private double shadeRatio = 7;
    
    /**
     * The required width and height of the final image
     */
    private int width;
    private int height;
    
    /**
     * The width that each of our threads is responsible
     * for in pixels, and in axis value.
     */
    private int threadWidth;
    private double threadAxisWidth;
    
    /**
     * These are the maximum and minimum axis values
     * Eg -2, 2 and -1.6, 1.6
     * 
     * axisXMax is not stored as it is never used
     */
    private double axisYMin;
    private double axisYMax;
    private double axisXMin;
    
    /**
     * These donate how the value changes on each
     * axis as you advance by one pixel
     */
    private double axisYStep;
    private double axisXStep;
    
    /**
     * If true, the image is ok to be drawn. If
     * false, the AlgorithmChecker has had its
     * values changed via public setters and so
     * will call update() before attempting to
     * draw the image, so that all required values
     * are correctly set before setting off the
     * child threads
     * 
     * @see update()
     */
    private boolean ready = false;
    
    /**
     * Should this be a Julia set or not?
     */
    protected boolean julia = false;
    
    /**
     * The ComplexNumber seed (c) if this is running
     * as a Julia set
     */
    protected ComplexNumber userSelectedPoint;
    
    /**
     * AlgoirthmChecker
     * 
     * Creates the appropriate number of threads
     * 
     * @param julia Should this be a julia set?
     */
    public AlgorithmChecker(boolean julia)
    {
        //It will create a thread per processor
        //Hopefully the system runs them simultaneously
        // - it barely ever does >:(
        double processors = (double)Runtime.getRuntime().availableProcessors();
        
        for (int i = 0; i < processors; i++)
        {
            threads.add(new CalculatorThread());
        }
        
        this.julia = julia;
    }
    
    /**
     * finishedDrawing
     * 
     * Called by a CalculatorThread to tell the checker
     * that it has finished.
     * 
     * When all Threads have finished, the listeners
     * will be notified.
     * 
     * @param ct The CalculatorThread that finished
     */
    public synchronized void finishedDrawing(CalculatorThread ct)
    {
        //If running is already empty, repaint will have been called
        //so we should go no further
        if (running.isEmpty()) return;
        
        running.remove(ct);
        
        //running is empty when all threads have completed
        if (running.isEmpty())
        {
            for (AlgorithmFinishedListener afl : listeners)
            {
                afl.finishedImage(image);
            }
        }
    }
    
    /**
     * addFinishedListener
     * 
     * Adds a listener to be called when the fractal
     * image has been calculated
     * 
     * @param afl The listener
     */
    public void addFinishedListener(AlgorithmFinishedListener afl)
    {
        listeners.add(afl);
    }
    
    /**
     * start
     * 
     * Sets the children running
     */
    public void start()
    {
        //Can sometimes be called twice by double tapping "redraw"
        //Quietly ignore these things
        if (running()) return;
        
        //If not ready, perform the update() method attempt to get
        //ready
        //If update returns false, we haven't got all the information
        //we need. This will only happen at startup, so it's ok to quietly
        //refuse to draw as it will be called again when the required
        //information is provided
        if (!ready && !update()) return;
        
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (CalculatorThread ct : threads)
        {
            running.add(ct);
            
            if (ct.isAlive())
            {
                ct.interrupt();
            }
            else
            {
                ct.start();
            }
        }
    }
    
    /**
     * running
     * 
     * Returns true if the image is being calculated.
     * I don't think it's ever used.
     * 
     * @return true if the image is being calculated
     */
    public boolean running()
    {
        return !running.isEmpty();
    }
    
    /**
     * update
     * 
     * Calculates the required values for the child
     * threads
     * 
     * @return true if it had the appropriate information
     *         false if information was missing
     */
    private boolean update()
    {
        axisXStep = threadAxisWidth / (double)threadWidth;
        axisYStep = Math.abs(axisYMax - axisYMin) / (double)height;
        
        //resize hasn't been called / the JFrame is too small
        if (threadWidth <= 0 || height <= 0)
        {
            return false;
        }
        
        int    x    = 0;
        double real = axisXMin;
        
        //Notify all the child threads about their bounds
        for (CalculatorThread ct : threads)
        {
            ct.setBounds(real, x, x + threadWidth);
            
            x    += threadWidth;
            real += threadAxisWidth;
        }
        
        ready = true;
        return true;
    }
    
    /**
     * resize
     * 
     * Change the size of the desired image
     * 
     * @param width  The required width
     * @param height The required height
     */
    public void resize(int width, int height)
    {
        if (running()) return;
        
        //Changes made, update() will be called
        //before drawing can begin
        ready       = false;
        
        this.width  = width;
        this.height = height;
        
        threadWidth = width / threads.size();
    }
    
    /**
     * changeAxis
     * 
     * Change the axis values to draw between
     * 
     * @param xMin The minimum x value
     * @param xMax The maximum x value
     * @param yMin The minimum y value
     * @param yMax The maximum y value
     */
    public void changeAxis(double xMin, double xMax, double yMin, double yMax)
    {
        if (running()) return;
        
        //Changes made, update() will be called
        //before drawing can begin
        ready     = false;
        
        axisYMin  = yMin;
        axisYMax  = yMax;
        axisXMin  = xMin;
        
        threadAxisWidth = Math.abs(xMax - xMin) / threads.size();
    }
    
    /**
     * setIterations
     * 
     * Set the maximum number of interations to do per pixel
     * 
     * @param iterations The number of iterations
     */
    public void setIterations(int iterations)
    {
        if (running()) return;
        
        this.iterations = iterations;
        this.shadeRatio = 765 / iterations; // 3 * 255 = 765
    }
    
    /**
     * setC
     * 
     * Sets the seed ComplexNumber. Called c because
     * that's the letter that was used in the
     * specification's algorithm.
     * 
     * @param userSelectedPoint The ComplexNumber seed
     */
    public void setC(ComplexNumber userSelectedPoint)
    {
        //Setting the userSelectedPoint halfway through a
        //calculation will cause striping
        if (this.running()) return;
        
        this.userSelectedPoint = userSelectedPoint;
    }
    
    /**
     * scaleShade
     * 
     * Scales up a number between 0 and iterations
     * to be a number between 0 and (3 * 255) for
     * use as a RGB value.
     * 
     * @param shade
     * @return
     */
    protected int scaleShade(int shade)
    {
        return 3 * 255 - (int)(shade * shadeRatio);
    }
    
    /**
     * calculate
     * 
     * Extending classes are responsible for calculating
     * Z()
     * 
     * @param d ComplexNumber
     * @param c ComplexNumber seed
     * @return The number of iterations required for divergence
     */
    abstract public int calculate(ComplexNumber d, ComplexNumber c);
    
    /**
     * calculate
     * 
     * Runs calculate(ComplexNumber, ComplexNumber)
     * with d as arg1 and d.clone() as arg2
     * 
     * @param d ComplexNumber
     * @return The number of iterations required for divergence
     */
    protected int calculate(ComplexNumber d)
    {
        return calculate(d, d.clone());
    }
    
    /**
     * CalculatorThread
     * 
     * @author Emily Shepherd
     */
    private class CalculatorThread extends Thread
    {
        /**
         * The minimum x axis value this thread is responsible
         * for
         */
        private double axisXMin;
        
        /**
         * The minimum and maximum x coords this thread is
         * responsible for
         */
        private int xMin;
        private int xMax;
        
        /**
         * setBounds
         * 
         * Saves the bounds this thread is responsible for
         * 
         * @param axisXMin
         * @param xMin
         * @param xMax
         */
        public void setBounds(double axisXMin, int xMin, int xMax)
        {
            this.axisXMin = axisXMin;
            
            this.xMin  = xMin;
            this.xMax  = xMax;
        }
        
        /**
         * run
         * 
         * Calculates this section of the image
         */
        public void run()
        {
            //Threads will sleep when inactive, not die
            while (true)
            {
                Graphics painter = image.getGraphics();
                double axisX        = axisXMin;
    
                //Two loops - one for x pixels, one for y pixles
                for (int x = xMin; x < xMax; x++)
                {
                    double axisY = axisYMax;
                    
                    for (int y = 0; y < height; y++)
                    {
                        //The result for this pixel
                        int test;
                        
                        if (julia)
                        {
                            test = calculate
                            (
                                new ComplexNumber(axisX, axisY),
                                userSelectedPoint
                            );
                        }
                        else
                        {
                            test = calculate(new ComplexNumber(axisX, axisY));
                        }
                        
                        //Scale the test result to a shade for the pixel
                        int shade = scaleShade(test);
                        
                        setColor(painter, shade);
                        
                        painter.drawLine(x, y, x, y);
                        
                        axisY -= axisYStep;
                    }
                    axisX += axisXStep;
                }
               
                try
                {
                    while (true)
                    {
                        //Tell the parent AlgorithmChecker this thread is done
                        finishedDrawing(this);
                        Thread.sleep(500);
                    }
                }
                //Woah! We has work to do!
                catch (InterruptedException ie) {}
            }
        }
        
        /**
         * setColor
         * 
         * Takes a number between 0 and 3 * 255,
         * splits it into three numbers between 0
         * and 255 and uses those to set the graphics'
         * color.
         * 
         * Can take a number higher than 3 * 255, if
         * you're extending my work like a n00b. (It
         * will ignore the heigher bits).
         * 
         * @param painter The Graphics object to set the color for
         * @param shade   The 0-3*255 number
         */
        private void setColor(Graphics painter, int shade)
        {
            int red   = shade > 255 ? 255 : shade;
            shade -= red;
            int green = shade > 255 ? 255 : shade;
            shade -= green;
            int blue  = shade > 255 ? 255 : shade;
            
            painter.setColor(new Color(red, green, blue));
        }
    }
}

/**
 * ComplexNumber
 * 
 * Represents a complex number, having a real
 * and imaginary section
 * 
 * @author Emily Shepherd
 *
 */
class ComplexNumber implements Cloneable
{
    /**
     * The real and imaginary parts of the
     * complex number
     */
    private double real;
    private double imaginary;
    
    /**
     * ComplexNumber
     * 
     * @param real The real part
     * @param imaginary The imaginary part
     */
    public ComplexNumber(double real, double imaginary)
    {
        this.real = real;
        this.imaginary = imaginary;
    }
    
    /**
     * getReal
     * 
     * Returns the real part of the number
     * 
     * @return The real part of the number
     */
    public double getReal()
    {
        return real;
    }
    
    /**
     * getImaginary
     * 
     * Returns the imaginary part of the number
     * 
     * @return The imaginary part of the number
     */
    public double getImaginary()
    {
        return imaginary;
    }
    
    /**
     * square
     * 
     * Squares the number
     */
    public void square()
    {
        double newReal = real*real - imaginary*imaginary;
        
        imaginary = 2 * real * imaginary;
        real      = newReal;
    }
    
    /**
     * modulusSquared
     * 
     * Returns the square of the modulus of the number
     * 
     * @return The square of the modulus of the number
     */
    public double modulusSquared()
    {
        return real*real + imaginary*imaginary;
    }
    
    /**
     * add
     * 
     * Adds the given ComplexNumber to this one
     * 
     * @param d The ComplexNumber to add
     */
    public void add(ComplexNumber d)
    {
        real      += d.getReal();
        imaginary += d.getImaginary();
    }
    
    /**
     * clone
     * 
     * Returns a clone of this object.
     * Not sure if it's ever used.
     * 
     * @return A clone
     */
    public ComplexNumber clone()
    {
        return new ComplexNumber(real, imaginary);
    }
    
    /**
     * toString
     * 
     * Returns string representation of the ComplexNumber
     * 
     * @return String representation of the ComplexNumber
     */
    public String toString()
    {
        return 
              getReal()      + " + "
            + getImaginary() + "i";
    }
    
    /**
     * equals
     * 
     * Returns true if the given ComplexNumber is
     * equal to this one
     * 
     * @param number The ComplexNumber to compare this to
     * @return true, if equal
     */
    public boolean equals(ComplexNumber number)
    {
        return number.getReal() == real && number.getImaginary() == imaginary;
    }
}

/**
 * Favourite
 * 
 * Represents a favourite Julia image
 * 
 * @author Emily Shepherd
 *
 */
class Favourite extends JPanel implements MouseListener
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * The image that we like
     */
    private BufferedImage image;
    
    /**
     * The seed number it came from
     */
    private ComplexNumber number;
    
    /**
     * The FavouriteViewer JFrame that this is part of
     */
    private FavouriteViewer parent;
    
    /**
     * This pane will have a preview of the image on it
     */
    private JPanel UIPane;
    
    /**
     * Favourite
     * 
     * 
     * 
     * @param bi The image
     * @param number The ComplexNumber seed
     * @param parent The parent Favourite JFrame
     */
    public Favourite(BufferedImage bi, ComplexNumber number, FavouriteViewer parent)
    {
        this.image  = bi;
        this.number = number;
        this.parent = parent;
        
        setLayout(new FlowLayout());
        
        UIPane = new JPanel()
        { 
            /**
             * 
             */
            private static final long serialVersionUID = 1L;

            public void paint(Graphics painter)
            {
                painter.drawImage(image, 0, 0, 120, 120, null);
            }
        };
        UIPane.setPreferredSize(new Dimension(120, 120));
        UIPane.setBackground(Color.black);
        add(UIPane);
        
        JLabel label = new JLabel(number.toString());
        add(label);
        
        JButton view = new JButton("View");
        view.addMouseListener(this);
        add(view);
        
        JButton save = new JButton("Save");
        save.addActionListener
        (
            new ActionListener()
            {
                public void actionPerformed(ActionEvent arg0)
                {
                    JFileChooser fc = new JFileChooser();

                    if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;
                    
                    File saveTo = fc.getSelectedFile();
                    
                    try
                    {
                        ImageIO.write(image, "png", saveTo);
                    }
                    catch (IOException e)
                    {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        );
        add(save);
    }

    /**
     * mouseClicked
     * 
     * Tells its parent that the mouse was clicked
     */
    public void mouseClicked(MouseEvent arg0)
    {
        parent.renderNumber(number);
    }

    /**
     * Unused
     * */
    public void mouseEntered(MouseEvent arg0){}
    public void mouseExited(MouseEvent arg0){}
    public void mousePressed(MouseEvent arg0){}
    public void mouseReleased(MouseEvent arg0){}
}

/**
 * FavouriteViewer
 * 
 * This JFrame stores and shows all favourites
 * 
 * @author Emily Shepherd
 *
 */
class FavouriteViewer extends JFrame
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * The parent JuliaUI
     */
    private JuliaUI parent;
    
    /**
     * FavouriteViewer
     * 
     * @param parent The parent JuliaUI
     */
    public FavouriteViewer(JuliaUI parent)
    {
        super("Favourites");
        
        this.parent = parent;
        
        setLayout(new FlowLayout());
        setSize(700, 700);
        setResizable(false);
    }
    
    /**
     * addFavourite
     * 
     * Adds a favourite to the FavouriteViewer
     * 
     * @param number The ComplexNumber it represents
     * @param bi The image
     */
    public void addFavourite(ComplexNumber number, BufferedImage bi)
    {
        add(new Favourite(bi, number, this));
    }
    
    /**
     * renderNumber
     * 
     * Tells its parent JuliaUI to render the number
     * (will recalculate it atm) and hides this JFrame.
     * 
     * @param number The ComplexNumber to draw
     */
    public void renderNumber(ComplexNumber number)
    {
        parent.renderNumber(number);
        setVisible(false);
    }
    
    /**
     * showForm
     * 
     * Alias for setVisible(true);
     */
    public void showForm()
    {
        setVisible(true);
    }
}

/**
 * JuliaUI
 * 
 * The JFrame that shows the Julia set
 * 
 * @author Emily Shepherd
 *
 */
class JuliaUI extends AbstractUI
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * The FavouriteViewer JFrame
     */
    private FavouriteViewer favs;
    
    /**
     * The label that shows the userSelectedPoint
     */
    private JLabel point;
    
    /**
     * JuliaUI
     * 
     * 
     */
    public JuliaUI(AlgorithmChecker ac)
    {
        super("Julia Set UI");
        
        setLayout(new BorderLayout());
        
        favs = new FavouriteViewer(this);
        
        //UIPane comes from parent class
        add(UIPane, BorderLayout.CENTER);
        
        //Controls
        JPanel controls = new JPanel();
        controls.setLayout(new FlowLayout());
        add(controls, BorderLayout.NORTH);
        
        //Add Favourite Button
        JButton fav = new JButton("Add Image to Favourites");
        fav.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent arg0)
            {
                favs.addFavourite(userSelectedPoint, image);
            }
        });
        controls.add(fav);
        
        //View Favourites Button
        JButton viewFavs = new JButton("View Favourites");
        viewFavs.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent arg0)
            {
                favs.showForm();
            }
        });
        controls.add(viewFavs);
        
        //Label
        point = new JLabel("");
        add(point, BorderLayout.SOUTH);
        
        //Set up the JuliaChecker with hard coded axis bounds
        this.ac = ac;
        this.ac.changeAxis(-2, 2, -1.6, 1.6);
        this.ac.setIterations(50); //Calculating on the fly is difficult, ok?
        this.ac.addFinishedListener(this);
        
        setSize(400, 300);
    }
    
    /**
     * renderNumber
     * 
     * Shows the JFrame and draws the number
     * 
     * @param c
     */
    public void renderNumber(ComplexNumber c)
    {
        //Ugly, but it has to be a JuliaChecker for
        //setC()
        ac.setC(c);
        userSelectedPoint = c;
        
        ac.start();
        
        point.setText(c.toString());
        
        //User may have closed the Window.
        //If so, open it again
        if (!this.isVisible())
        {
            setVisible(true);
        }
    }
    
    /**
     * running
     * 
     * Returns true if the image is being calculated
     * 
     * @return True if the image is being calculated
     */
    public boolean running()
    {
        return ac.running();
    }
    
    /**
     * setIterations
     * 
     * Sets the iterations for the JuliaChecker
     * 
     * @param interations The number of iterations
     * @see   AlgorithmChecker.setIterations
     */
    public void setIterations(int interations)
    {
        ac.setIterations(interations);
    }
}

/**
 * MandelbrotChecker
 * 
 * Holds the algorithm for calculating the
 * Mandelbrot set
 * 
 * @author Emily Shepherd
 */
class MandelbrotChecker extends AlgorithmChecker
{
    public MandelbrotChecker(boolean julia)
    {
        super(julia);
    }
    
    /**
     * calculate
     * 
     * @param d ComplexNumber
     */
    public int calculate(ComplexNumber d, ComplexNumber c)
    {
        for (int i = 1; i < iterations; i++)
        {
            d.square();
            d.add(c);
            
            if (d.modulusSquared() > 4)
            {
                return i;
            }
        }
        
        return iterations;
    }
}

/**
 * MandelbrotUI
 * 
 * JFrame for showing the Mandelbrot image
 * 
 * @author Emily Shepherd
 */
class MandelbrotUI extends AbstractUI
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * Array of text boxes in the controls
     */
    private JTextField[] boxes = new JTextField[5];
    
    /**
     * The JuliaUI JFrame
     * 
     * @see JuliaUI
     */
    private JuliaUI julia;
    private JuliaUI[] julias;
    
    /**
     * Algorithms
     */
    private AlgorithmChecker[] checkers;
    
    /**
     * This nested class is responsible for
     * following dragging and drawing the zooming
     * rectangle
     * 
     * @see MandelbrotUI.DragFollower
     */
    private DragFollower df = new DragFollower();
    
    /**
     * MandelbrotUI
     * 
     */
    public MandelbrotUI()
    {
        super("Mandelbrot UI");
        
        checkers = new AlgorithmChecker[2];
        julias   = new JuliaUI[2];
        
        //Setup MandelbrotCheck with default axis values
        checkers[0] = new MandelbrotChecker(false);//MandelbrotChecker();
        checkers[0].addFinishedListener(this);
        checkers[1] = new BurningShipChecker(false);//MandelbrotChecker();
        checkers[1].addFinishedListener(this);
        
        ac = checkers[0];
        ac.changeAxis(-2, 2, -1.6, 1.6);
        
        julias[0] = new JuliaUI(new MandelbrotChecker(true));
        julias[1] = new JuliaUI(new BurningShipChecker(true));
        julia     = julias[0];
        
        //This is the main window, closing it should
        //end the program
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);     
        
        setLayout(new BorderLayout());
        
        this.setExtendedState(Frame.MAXIMIZED_BOTH);
        
        //UIPane comes from parent class
        UIPane.setBackground(Color.cyan);
        UIPane.addMouseMotionListener(df);
        UIPane.addMouseListener(df);
        add(UIPane,  BorderLayout.CENTER);
        
        //controls
        JPanel controls = new JPanel();
        controls.setLayout(new FlowLayout());
        add(controls, BorderLayout.SOUTH);
        
        //Checkers combo box
        JComboBox checkersCombo = new JComboBox(new String[] {"Mandelbrot", "Buring Ship"});
        checkersCombo.addActionListener
        (
            new ActionListener()
            {
                /**
                 * Puts the appropiate ac and juliaUI in place then
                 * calls reset to ensure they have correct initial
                 * values
                 * 
                 * @see reset()
                 */
                public void actionPerformed(ActionEvent e)
                {
                    JComboBox combo = (JComboBox)e.getSource();
                    ac              = checkers[combo.getSelectedIndex()];
                    julia           = julias[combo.getSelectedIndex()];
                    reset();
                }
            }
        );
        controls.add(checkersCombo);
        
        //Add text boxes
        addBox(0, "Minimum X axis value:", "-2",   controls);
        addBox(1, "Maximum X axis value:", "2",    controls);
        addBox(2, "Minimum Y axis value:", "-1.6", controls);
        addBox(3, "Maximum Y axis value:", "1.6",  controls);
        addBox(4, "Iterations:",           "100",  controls);
        
        //Redraw button
        //This button updates the Mandelbrot axis values
        JButton draw = new JButton("Redraw");
        draw.addActionListener
        (
            new ActionListener()
            {
                /**
                 * actionPerformed
                 * 
                 * Updates the iterations and axis values
                 * for the MandelbrotChecker and tells it
                 * to redraw.
                 * 
                 * Bit annoying if nothing has changed as it
                 * will redraw anyway
                 */
                public void actionPerformed(ActionEvent arg0)
                {
                    double uMinX  = new Double(boxes[0].getText()).doubleValue();
                    double uMaxX  = new Double(boxes[1].getText()).doubleValue();
                    double uMinY  = new Double(boxes[2].getText()).doubleValue();
                    double uMaxY  = new Double(boxes[3].getText()).doubleValue();

                    ac.setIterations(new Integer(boxes[4].getText()));
                    ac.changeAxis(uMinX, uMaxX, uMinY, uMaxY);
                    ac.start();
                }
            }
        );
        controls.add(draw);
        
        //Reset button
        JButton reset = new JButton("Reset");
        reset.addActionListener
        (
            new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    reset();
                }
            }
        );
        controls.add(reset);     
        
        //Always should be visible
        setVisible(true);
    }
    
    /**
     * addBox
     * 
     * Adds a box to the controls JPanel
     * 
     * @param i The position to put this in the boxes array
     * @param text The text for its label
     * @param val The initial value for the box
     * @param parent The controls JPanel
     */
    private void addBox(int i, String text, String val, JPanel parent)
    {
        boxes[i]     = new JTextField(3);
        boxes[i].setText(val);
        JLabel label = new JLabel(text);
        
        parent.add(label);
        parent.add(boxes[i]);
    }
    
    /**
     * reset
     * 
     * Resets the UI.
     *   Sets the axis to default (-2, -1.6) -> (2, 1.6)
     *   Sets the iterations to default (100)
     *   Forces the recalculation of the width of the display
     *   Updates the text boxes
     *   Repaints
     */
    private void reset()
    {
        boxes[0].setText("-2");
        boxes[1].setText("2");
        boxes[2].setText("-1.6");
        boxes[3].setText("1.6");
        boxes[4].setText("100");
        
        ac.setIterations(100);
        ac.changeAxis(-2, 2, -1.6, 1.6);
        componentResized(null); //calls repaint()
    }
    
    /**
     * paint
     * 
     * Paints above and prompts the DragFollower to
     * paint()
     * 
     * @see DragFollower.paint()
     */
    public void paint(Graphics painter)
    {
        super.paint(painter);
        
        df.paint(painter);
    }
    
    /**
     * getPoint
     * 
     * Converts an (x, y) coordinate into a ComplexNumber
     * 
     * @param x The x coordinate
     * @param y The y coordinate
     * @return The ComplexNumber for that point
     */
    private ComplexNumber getPoint(int x, int y)
    {
        //User defined minimum and maximums
        //This might not work if the user has changed these values
        //but hasn't yet hit redraw.
        //Hmmm... stupid users
        double uMinX  = new Double(boxes[0].getText()).doubleValue();
        double uMaxX  = new Double(boxes[1].getText()).doubleValue();
        double uMinY  = new Double(boxes[2].getText()).doubleValue();
        double uMaxY  = new Double(boxes[3].getText()).doubleValue();
        //How much to increment the Mandelbrot values by each iteration
        double ratioX = Math.abs(uMaxX - uMinX) / UIPane.getWidth();
        double ratioY = Math.abs(uMaxY - uMinY) / UIPane.getHeight();
        
        return new ComplexNumber
        (
            uMinX + ratioX * x,
            uMaxY - ratioY * y  //Minus because pixel y values are in reverse order to axis
        );
    }
    
    /**
     * DragFollower
     * 
     * Responsible for following the users click and drag
     * to zoom, and for drawing a rectangle to match that
     * 
     * @author Emily Shepherd
     *
     */
    private class DragFollower implements MouseListener, MouseMotionListener
    {
        /**
         * Where they started dragging
         */
        private MouseEvent startPoint;
        
        /**
         * Where they are now
         */
        private MouseEvent currentPoint;
        
        /**
         * Used to redraw the screen every 70ms
         */
        private Timer timer;
        
        /**
         * Last ComplexNumber we drew last
         * If it is the same, the screen won't
         * be redrawn unnecessarily
         */
        private ComplexNumber lastDrawn;
        
        /**
         * If true, it will redraw the JuliaUI in real
         * time as you move your mouse.
         */
        private boolean realTime = false;
        
        /**
         * DragFollower
         * 
         */
        public DragFollower()
        {
            timer = new Timer();
            reschedule();
        }
        
        /**
         * mouseDragged
         * 
         * Follows the mouse being dragged to zoom
         */
        public void mouseDragged(MouseEvent e)
        {
            if (startPoint == null)
            {
                startPoint = e;
            }
            else
            {
                currentPoint = e;
            }
        }
        
        /**
         * reschedule
         * 
         * Reschedules the timer with our TimerTask
         */
        private void reschedule()
        {
            timer.schedule
            (
                new TimerTask()
                {
                    /**
                     * run
                     * 
                     * Does the task
                     */
                    public void run()
                    {
                        //We aren't on the screen
                        //Drawing is pointless
                        if (currentPoint == null)
                        {
                            reschedule();
                            return;
                        }
                        
                        ComplexNumber thisPoint = getPoint(currentPoint.getX(), currentPoint.getY());
                        
                        //If we've already dealt with this point, there's nothing
                        //to do here
                        if (lastDrawn != null && lastDrawn.equals(thisPoint))
                        {
                            reschedule();
                            return;
                        }
                        
                        lastDrawn = thisPoint;
                        
                        //Real time drawing of the Julia set
                        //Calling AlgorithmChecker functions when it is
                        //running will be ignored, so the running() check
                        //isn't strictly necessary - its just to stop pointless
                        //checks down the line
                        if (realTime && !julia.running())
                        {
                            julia.renderNumber(thisPoint);
                        }
                        
                        //Repaint the screen for the dragging box
                        if (startPoint != null)
                        {
                            repaint();
                        }
                        
                        reschedule();
                    }
                },
                70
            );
        }
        
        /**
         * paint
         * 
         * Paints the red rectangle
         * 
         * @param _painter Ignores this, so that's good
         */
        public void paint(Graphics _painter)
        {
            //If we don't have a startPoint, we weren't dragging
            if (startPoint == null) return;

            Graphics painter = UIPane.getGraphics();
            painter.setColor(Color.red);
            
            int coords[] = getCurrentCoords();
            
            painter.drawRect(coords[0], coords[1], coords[2], coords[3]);
        }
        
        /**
         * getCurrentCoords
         * 
         * Calculates the top left coords, width and
         * height of our required rectangle from
         * currentPoint and startPoint, because
         * Graphics.drawRect() doesn't like negative
         * widths, heights apparently.
         * 
         * @return Array of coordinates
         */
        private int[] getCurrentCoords()
        {
            int topX   = startPoint.getX();
            int topY   = startPoint.getY();
            int width  = currentPoint.getX() - startPoint.getX();
            int height = currentPoint.getY() - startPoint.getY();
            
            //Minus widths / heights are bad. So flip the values
            //if they are.
            if (currentPoint.getX() < topX)
            {
                topX   = currentPoint.getX();
                width *= -1;
            }
            if (currentPoint.getY() < topY)
            {
                topY    = currentPoint.getY();
                height *= -1;
            }
            
            return new int[] {topX, topY, width, height};
        }
        
        /**
         * mouseReleased
         * 
         * If dragging was in operation, this tells
         * the controls the new axis bounds and sets the
         * MandelbrotChecker redrawing the fractal in
         * the zoomed in view.
         */
        public void mouseReleased(MouseEvent arg0)
        {
            //Nothing is happening here
            if (timer == null || startPoint == null || currentPoint == null) return;
            
            int topX    = startPoint.getX();
            int topY    = startPoint.getY();
            int bottomX = currentPoint.getX();
            int bottomY = currentPoint.getY();
            
            if (bottomX > topX)
            {
                topX    = bottomX;
                bottomX = startPoint.getX();
            }
            if (bottomY > topY)
            {
                topY    = bottomY;
                bottomY = startPoint.getY();
            }
            
            ComplexNumber top    = getPoint(topX, bottomY);
            ComplexNumber bottom = getPoint(bottomX, topY);
          
            //Tell the AlgorithmChecker, and set that off
            ac.changeAxis(bottom.getReal(), top.getReal(), bottom.getImaginary(), top.getImaginary());
            ac.start();
            
            //Update the GUI controls
            boxes[0].setText(new Double(bottom.getReal()).toString());
            boxes[1].setText(new Double(top.getReal()).toString());
            boxes[2].setText(new Double(bottom.getImaginary()).toString());
            boxes[3].setText(new Double(top.getImaginary()).toString());
            
            //Stop dragging
            startPoint = null;
        }

        /**
         * mouseMoved
         * 
         * Keeps tabs on the mouse position by saving
         * it to currentPoint
         */
        public void mouseMoved(MouseEvent e)
        {
            currentPoint = e;
        }
        
        /**
         * mouseClicked
         * 
         * Switches live following on / off
         * and updates the iterations - live gets 50,
         * static gets 100
         * 
         */
        public void mouseClicked(MouseEvent e)
        {
            realTime = !realTime;
            
            if (realTime)
            {
                julia.setIterations(50);
                
                julia.renderNumber(getPoint(e.getX(), e.getY()));
            }
            else
            {
                julia.setIterations(100);
                
                userSelectedPoint = getPoint(e.getX(), e.getY());
                
                julia.renderNumber(userSelectedPoint);
            }
        }
        
        /**
         * Unused
         */
        public void mouseEntered(MouseEvent arg0) {}
        public void mouseExited(MouseEvent arg0) {}
        public void mousePressed(MouseEvent e){}
    }
}

/**
 * BurningShipChecker
 */
class BurningShipChecker extends AlgorithmChecker
{
    /**
     * Calls parent
     * 
     * @param julia Is this a Julia set?
     */
    public BurningShipChecker(boolean julia)
    {
        super(julia);
    }

    /**
     * calculate
     * 
     * Calculate the factal
     */
    public int calculate(ComplexNumber d, ComplexNumber c)
    {
        for (int i = 1; i < iterations; i++)
        {
            d = new ComplexNumber
            (
                Math.abs(d.getReal()),
                Math.abs(d.getImaginary())
            );
            d.square();
            d.add(c);
            
            if (d.modulusSquared() > 4)
            {
                return i;
            }
        }
        
        return iterations;
    }
}
