import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.Arrays;
import java.util.Random;

public class ImageProcess {
  public static void main (String[] args){
    try {
      // 讀原檔original.jpeg
      File input = new File("iu.png");
      BufferedImage image = ImageIO.read(input);

      // 讀取原始影像長寬
      int width = image.getWidth();
      int height = image.getHeight();
      // 讀取原始影像pixels
      int[][] image_pixels = new int[height][width];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          image_pixels[y][x] = image.getRGB(x, y);
        }
      }

      // getGrayImage -> 做（灰階轉換）
      // return 灰階pixels
      int[][] gray_pixels = getGrayImage(image_pixels, height, width);

      // getNegativeImage -> 利用灰階pixels，再進行（負片轉換）
      getNegativeImage(gray_pixels, height, width);

      // 第1行的運算
      // getGammaImage 小於1 -> 利用灰階pixels，再進行（gamma轉換）
      // return gamma pixels
      int[][] gamma_pixels_below_1 = getGammaImage(gray_pixels, height, width, 0.5);
      // getSaltAndPaper -> 利用gamma pixels，再進行（胡椒鹽轉換）
      // return 胡椒鹽pixels
      int[][] snp_pixels = getSaltAndPepper(gamma_pixels_below_1, height, width, 5);
      // get3x3MedianFilter -> 利用胡椒鹽pixels，再進行（中位數轉換）
      get3x3MedianFilter(snp_pixels, height, width);

      get3x3MeanFilter(snp_pixels, height, width);
      
      
      // 第2行的運輸
      // getContrastStretching -> 利用灰階pixels，再進行（對比拉開）
      // return 對比拉開pixels
      int[][] cs_pixels = getContrastStretching(gray_pixels, height, width);
      sobelEdgeDetecting(cs_pixels, height, width);
      int[][] lap_pixels = getLaplacian(cs_pixels, height, width);
      get3x3MaxFilter(lap_pixels, height, width);

      // 第3行的運輸·
      // getGammaImage 大於1 -> 利用灰階pixels，再進行（gamma轉換）
      // return gamma pixels
      int[][] gamma_pixels_above_1 = getGammaImage(gray_pixels, height, width, 3);
      // 利用gamma pixels -> 利用gamma pixels，再進行（二值化（平均值當門檻值）轉換）
      getBinarization(gamma_pixels_above_1, height, width);
      
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }
  }

  static int[][] getGrayImage(int[][] pixels, int height, int width) {
    // 定義一張相同width height的影像
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); 
    // 設定需要return的pixels參數
    int[][] new_pixels = new int[height][width];
    // 對每個pixels運算
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int p = pixels[y][x];
        // 在原始影像pixels裡取得rgb值
        // pixel值位移16， 再用0xff取到2^8之間
        int r = (p >> 16) & 0xff;
        int g = (p >> 8) & 0xff;
        int b = p & 0xff;
        // 透過rgb相加再除以3取得灰階值
        int avg = (r + g + b) / 3;
        p = (avg << 16) | (avg << 8) | avg;
        // 需要回傳的灰階值，後續運算使用
        new_pixels[y][x] = p;
        // 再對新影像設定新的灰階值
        image.setRGB(x, y, p);
      }
    }
    // 再來是寫圖
    try {
      File output = new File("output/grayscale.png");
      ImageIO.write(image, "png", output);
    } catch (IOException e) {
      System.out.println("Error: " + e);
    }
    return pixels;
  }

  static void getNegativeImage(int[][] pixels, int height, int width) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); 

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int p = pixels[y][x] & 0xff;
        // 255 - 灰階值 = 負片值
        int negative = 255 - p;
        p = (negative << 16) | (negative << 8) | negative;
        image.setRGB(x, y, p);
      }
    }

    try {
      File output = new File("output/negative.png");
      ImageIO.write(image, "png", output);
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }
  }

  static int[][] getGammaImage(int[][] pixels, int height, int width, double gamma) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); 
    int[][] new_pixels = new int[height][width];
    
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int p = pixels[y][x] & 0xff;
        // (pixel值/255 ^ gamma) * 255
        double new_p_double = Math.pow((double)(p)/255, gamma) * 255;
        int new_p = (int)(new_p_double);
        p = (new_p << 16) | (new_p << 8) | new_p;
        new_pixels[y][x] = p;
        image.setRGB(x, y, p);
      }
    }

    try {
      File output = new File("output/gamma"+gamma+".png");
      ImageIO.write(image, "png", output);
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }
    return new_pixels;
  }

  static int[][] getContrastStretching(int[][] pixels, int height, int width) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); 
    int[][] new_pixels = new int[height][width];
    int max = 0;
    int min = 255;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int p = pixels[y][x] & 0xff;
        if(p > max) {
          max = p;
        }
        if(p < min) {
          min = p;
        }
      }
    }

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int p = pixels[y][x] & 0xff;
        // (pixel值 - min / max - min) * 255
        double cs_float = (double) (p - min) / (max - min) * 255;
        int cs = (int) cs_float;
        p = (cs << 16) | (cs << 8) | cs;
        new_pixels[y][x] = p;
        image.setRGB(x, y, p);
      }
    }

    try {
      File output = new File("output/contraststretch.png");
      ImageIO.write(image, "png", output);
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }
    return new_pixels;
  }

  static int[][] getSaltAndPepper(int[][] pixels, int height, int width, int percent) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    int[][] new_pixels = new int[height][width];

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int p = pixels[y][x] & 0xff;
        // 利用percent取隨機數
        int rand = new Random().nextInt(percent);
        // 數值有match就增加雜訊，雜訊0黑255白
        if (rand == percent-1) {
          int[] array = new int[2];
          array[0] = 0;
          array[1] = 255;
          int noise = array[new Random().nextInt(array.length)];

          p = (noise << 16) | (noise << 8) | noise;
        } else {
          p = (p << 16) | (p << 8) | p;
        }
        new_pixels[y][x] = p;
        image.setRGB(x, y, p);
      }
    }

    try {
      File output = new File("output/saltpepper.png");
      ImageIO.write(image, "png", output);
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }

    return new_pixels;
  }

  static void get3x3MeanFilter(int[][] pixels, int height, int width) {
    /*
      |0|1|2|   |(y-1,x-1)|(y-1,x)|(y-1,x+1)|
      |3|4|5|   |(y  ,x-1)|(y , x)|(y  ,x+1)|
      |6|7|8|   |(y+1,x-1)|(y+1,x)|(y+1,x+1)|
    */
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    int[] pixel_of_3x3 = new int[9];

    for (int y = 1; y < height-1; y++) {
      for (int x = 1; x < width-1; x++) {
        pixel_of_3x3[0] = pixels[y-1][x-1] & 0xff;
        pixel_of_3x3[1] = pixels[y][x-1] & 0xff;
        pixel_of_3x3[2] = pixels[y+1][x-1] & 0xff;
        pixel_of_3x3[3] = pixels[y-1][x] & 0xff;
        pixel_of_3x3[4] = pixels[y][x] & 0xff;
        pixel_of_3x3[5] = pixels[y+1][x] & 0xff;
        pixel_of_3x3[6] = pixels[y-1][x+1] & 0xff;
        pixel_of_3x3[7] = pixels[y][x+1] & 0xff;
        pixel_of_3x3[8] = pixels[y+1][x+1] & 0xff;

        int sum = 0;
        for (int k = 0; k < pixel_of_3x3.length; k++) {
          sum = sum + pixel_of_3x3[k];
        }

        int g = sum / pixel_of_3x3.length;
        int avg = (g << 16) | (g << 8) | g;

        int p = (avg << 16) | (avg << 8) | avg;

        image.setRGB(x, y, p);
      }
    }
    try {
      File output = new File("output/mean3x3.png");
      ImageIO.write(image, "png", output);
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }
  }

  static void get3x3MedianFilter(int[][] pixels, int height, int width) {
    /*
      |0|3|6|   |(y-1,x-1)|(y-1,x)|(y-1,x+1)|
      |1|4|7|   |(y  ,x-1)|(y , x)|(y  ,x+1)|
      |2|5|8|   |(y+1,x-1)|(y+1,x)|(y+1,x+1)|
    */
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    int[] pixel_of_3x3 = new int[9];

    for (int y = 1; y < height-1; y++) {
      for (int x = 1; x < width-1; x++) {
        pixel_of_3x3[0] = pixels[y-1][x-1] & 0xff;
        pixel_of_3x3[1] = pixels[y][x-1] & 0xff;
        pixel_of_3x3[2] = pixels[y+1][x-1] & 0xff;
        pixel_of_3x3[3] = pixels[y-1][x] & 0xff;
        pixel_of_3x3[4] = pixels[y][x] & 0xff;
        pixel_of_3x3[5] = pixels[y+1][x] & 0xff;
        pixel_of_3x3[6] = pixels[y-1][x+1] & 0xff;
        pixel_of_3x3[7] = pixels[y][x+1] & 0xff;
        pixel_of_3x3[8] = pixels[y+1][x+1] & 0xff;

        Arrays.sort(pixel_of_3x3);
        int p = (pixel_of_3x3[4] << 16) | (pixel_of_3x3[4] << 8) | pixel_of_3x3[4];

        image.setRGB(x, y, p);
      }
    }
    try {
      File output = new File("output/median3x3.png");
      ImageIO.write(image, "png", output);
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }
  }

  static int[][] getLaplacian(int[][] pixels, int height, int width) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); 
    int[][] new_pixels = new int[height][width];
    int[] pixel_of_3x3 = new int[9];
    // 0 1 0
    // 1 -4 1
    // 0 1 0
    
    for (int y = 1; y < height-1; y++) {
      for (int x = 1; x < width-1; x++) {
        pixel_of_3x3[0] = pixels[y-1][x-1] & 0xff;
        pixel_of_3x3[1] = pixels[y][x-1] & 0xff;
        pixel_of_3x3[2] = pixels[y+1][x-1] & 0xff;
        pixel_of_3x3[3] = pixels[y-1][x] & 0xff;
        pixel_of_3x3[4] = pixels[y][x] & 0xff;
        pixel_of_3x3[5] = pixels[y+1][x] & 0xff;
        pixel_of_3x3[6] = pixels[y-1][x+1] & 0xff;
        pixel_of_3x3[7] = pixels[y][x+1] & 0xff;
        pixel_of_3x3[8] = pixels[y+1][x+1] & 0xff;

        int g = Math.abs(((0*pixel_of_3x3[0])+(1*pixel_of_3x3[3])+(0*pixel_of_3x3[6]))
                +((1*pixel_of_3x3[1])+(-4*pixel_of_3x3[4])+(1*pixel_of_3x3[7]))
                +((0*pixel_of_3x3[2])+(1*pixel_of_3x3[5])+(0*pixel_of_3x3[8])));

        int p = (g << 16) | (g << 8) | g;
        new_pixels[y][x] = p;
        image.setRGB(x, y, p);
      }
    }

    try {
      File output = new File("output/laplacian.png");
      ImageIO.write(image, "png", output);
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }
    return new_pixels;
  }

  static void get3x3MaxFilter(int[][] pixels, int height, int width) {
    /*
      |0|3|6|   |(y-1,x-1)|(y-1,x)|(y-1,x+1)|
      |1|4|7|   |(y  ,x-1)|(y , x)|(y  ,x+1)|
      |2|5|8|   |(y+1,x-1)|(y+1,x)|(y+1,x+1)|
    */
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    int[] pixel_of_3x3 = new int[9];
    
    for (int y = 1; y < height-1; y++) {
      for (int x = 1; x < width-1; x++) {
        pixel_of_3x3[0] = pixels[y-1][x-1] & 0xff;
        pixel_of_3x3[1] = pixels[y][x-1] & 0xff;
        pixel_of_3x3[2] = pixels[y+1][x-1] & 0xff;
        pixel_of_3x3[3] = pixels[y-1][x] & 0xff;
        pixel_of_3x3[4] = pixels[y][x] & 0xff;
        pixel_of_3x3[5] = pixels[y+1][x] & 0xff;
        pixel_of_3x3[6] = pixels[y-1][x+1] & 0xff;
        pixel_of_3x3[7] = pixels[y][x+1] & 0xff;
        pixel_of_3x3[8] = pixels[y+1][x+1] & 0xff;

        Arrays.sort(pixel_of_3x3);
        int max = pixel_of_3x3[pixel_of_3x3.length - 1];
        int p = (max << 16) | (max << 8) | max;
        image.setRGB(x, y, p);
      }
    }
    try {
      File output = new File("output/max3x3.png");
      ImageIO.write(image, "png", output);
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }
  }

  static void getBinarization(int[][] pixels, int height, int width) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    int sum = 0;
    
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        sum = sum + (pixels[y][x] & 0xff);
      }
    }

    // 平均值為門檻值
    int threshold = sum / (width*height);;

    for (int y = 1; y < height-1; y++) {
      for (int x = 1; x < width-1; x++) {
        int g = 0;
        if(pixels[y][x] >= threshold) {
          g = 255;
        } else {
          g = 0;
        }
        int p = (g << 16) | (g << 8) | g;
    
        image.setRGB(x, y, p);
      }
    }

    try {
      File output = new File("output/binarization.png");
      ImageIO.write(image, "png", output);
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }
  }

  static void sobelEdgeDetecting(int[][] pixels, int height, int width) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    int[] pixel_of_3x3 = new int[9];

    for (int y = 1; y < height-1; y++) {
      for (int x = 1; x < width-1; x++) {
        pixel_of_3x3[0] = pixels[y-1][x-1] & 0xff;
        pixel_of_3x3[1] = pixels[y][x-1] & 0xff;
        pixel_of_3x3[2] = pixels[y+1][x-1] & 0xff;
        pixel_of_3x3[3] = pixels[y-1][x] & 0xff;
        pixel_of_3x3[4] = pixels[y][x] & 0xff;
        pixel_of_3x3[5] = pixels[y+1][x] & 0xff;
        pixel_of_3x3[6] = pixels[y-1][x+1] & 0xff;
        pixel_of_3x3[7] = pixels[y][x+1] & 0xff;
        pixel_of_3x3[8] = pixels[y+1][x+1] & 0xff;

        // -1 0 1
        // -2 0 2
        // -1 0 1
        int gx = (((-1*pixel_of_3x3[0])+(0*pixel_of_3x3[3])+(1*pixel_of_3x3[6]))
                +((-2*pixel_of_3x3[1])+(0*pixel_of_3x3[4])+(2*pixel_of_3x3[7]))
                +((-1*pixel_of_3x3[2])+(0*pixel_of_3x3[5])+(1*pixel_of_3x3[8])));
        // -1 -2 -1
        //  0  0  0
        //  1  2  1
        int gy = (((-1*pixel_of_3x3[0])+(-2*pixel_of_3x3[3])+(-1*pixel_of_3x3[6]))
                +((0*pixel_of_3x3[1])+(0*pixel_of_3x3[4])+(0*pixel_of_3x3[7]))
                +((1*pixel_of_3x3[2])+(2*pixel_of_3x3[5])+(1*pixel_of_3x3[8])));

        double gval = Math.sqrt((gx*gx)+(gy*gy));
        int g = (int)(gval);

        int p = (g << 16) | (g << 8) | g;

        image.setRGB(x, y, p);
      }
    }
    try {
      File output = new File("output/sobel.png");
      ImageIO.write(image, "png", output);
    } catch(IOException e) {
      System.out.println("Error: " + e);
    }
  }
}