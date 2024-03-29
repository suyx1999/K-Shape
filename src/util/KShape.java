package util;

import util.Jama.Matrix;
import util.Jama.EigenvalueDecomposition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


public class KShape {
    private int num_clusters;
    private int max_iter;
    private double [][] centroids;

    public KShape(int num_clusters, int max_iter){
        this.num_clusters = num_clusters;
        this.max_iter = max_iter;
    }

    public double[][] fit(double[][] X){
        int len = X[0].length;
        this.centroids = new double[num_clusters][len];
        this._kshape(X, this.num_clusters, this.max_iter);

        return this.centroids;
    }

    public static double[] _ncc(double[] x1, double[] x2){
        double den = _norm(x1) * _norm(x2);
        if (den < 1e-9)
            den = Double.MAX_VALUE;
        int x_len = x1.length;
        int fft_size = (int) Math.pow(2, Integer.toBinaryString(2*x_len-1).length());
        double[] cc=  FFT.ifft(Complex.multiply(FFT.fft(x1, fft_size),
                Complex.conjugate(FFT.fft(x2, fft_size))));
        double[] ncc = new double[fft_size - 1];
        // [-(x_len-1):] + [:x_len]
        for (int i = 0; i < fft_size - 1; i++)
            if (i < x_len - 1)
                ncc[i] = cc[fft_size - x_len + 1 + i] / den;
            else
                ncc[i] = cc[i - x_len + 1] / den;
        return ncc;
    }

    private void _kshape(double[][] X, int k, int max_iter){
        int n = X.length;
        int l = X[0].length;

        Random random = new Random();
        int[] idx = new int[n];
        int[] old_idx;
//        for (int i = 0; i < n; i++)
//            idx[i] = random.nextInt(k+1);

        double[][] _centroids = new double[k][l];
        double[][] distances = new double[n][k];

        for (int iter = 0; iter < max_iter; ++iter){
            old_idx = idx;

            for (int j = 0; j < k; ++j)
                _centroids[j] = _extractShape(idx, X, j, _centroids[j]);

            for (int i = 0; i < n; ++i)
                for (int j = 0; j < k; ++j)
                    distances[i][j] = 1 - Arrays.stream(_ncc(X[i], _centroids[j])).max().getAsDouble();

            idx = _argmin(distances);

            if (Arrays.equals(idx, old_idx))
                break;
        }
        this.centroids = _centroids;
    }

    public double[] _extractShape(int[] idx, double[][] X, int j, double[] curCentroid){
        int l = X[0].length;

        List<double[]> _a = new ArrayList<>();
        for (int i = 0; i < idx.length; i++)
            if (idx[i] == j)
                _a.add(X[i]);

        if (_a.isEmpty()) {
            Random random = new Random();
            return X[random.nextInt(X.length)];
        }

        double[][] _matrix = new double[_a.size()][l];
        for (int i = 0; i < _a.size(); i++)
            _matrix[i] = _a.get(i);
        Matrix y = new Matrix(_zscore(_matrix));

        Matrix s = y.transpose().times(y);
        double[][] _p = new double[l][l];
        for (int u = 0; u < l; ++u){
            for (int v = 0; v < l; ++v){
                _p[u][v] = 1.0 / (l - 1);
            }
        }
        Matrix p = new Matrix(_p);
        p = Matrix.identity(l, l).minus(p);
        Matrix m = p.times(s).times(p);

        EigenvalueDecomposition E =
                new EigenvalueDecomposition(m);
        Matrix eigenVectors = E.getV();
        double[] eigenValues = E.getRealEigenvalues();
        int _maxIdx = -1;
        double _maxVal = Double.MIN_VALUE;
        for (int i = 0; i < eigenValues.length; ++i){
            if (eigenValues[i] > _maxVal){
                _maxVal = eigenValues[i];
                _maxIdx = i;
            }
        }
        double[] newCentroid = eigenVectors.transpose().getArray()[_maxIdx];

        double dis1 = 0.0, dis2 = 0.0;
        for (double[] x: X){
            for (int i = 0; i < l; ++i){
                dis1 += Math.pow(x[i] - newCentroid[i], 2);
                dis2 += Math.pow(x[i] + newCentroid[i], 2);
            }
        }
        if (dis2 < dis1){
            for (int i = 0; i < l; ++i){
                newCentroid[i] = -newCentroid[i];
            }
        }
        return _zscore(newCentroid);
    }

    private static double _norm(double[] x){
        double res = 0.0;
        for (double v : x) {
            res += v * v;
        }
        return Math.sqrt(res);
    }

    private int[] _argmin(double[][] array) {
        int[] idx = new int[array.length];

        for (int i = 0; i < array.length; i++) {
            double min = array[i][0];
            int minIdx = 0;

            for (int j = 1; j < array[i].length; j++) {
                if (array[i][j] < min) {
                    min = array[i][j];
                    minIdx = j;
                }
            }

            idx[i] = minIdx;
        }

        return idx;
    }

    private double[] _zscore(double[] X){
        int l = X.length;
        double sum = 0.0;
        for (int j = 0; j < l; ++j){
            sum += X[j];
        }
        double mean = sum / l;

        sum = 0.0;
        for (int j = 0; j < l; ++j) {
            sum += Math.pow(X[j] - mean, 2);
        }
        double std = Math.sqrt(sum / (l-1));

        double[] res = new double[l];
        for (int j = 0; j < l; j++){
            res[j] = (X[j] - mean) / std;
        }
        return res;
    }

    private double[][] _zscore(double[][] X){
        int n = X.length;
        int l = X[0].length;
        double[] mean = new double[n];
        double[] std = new double[n];
        double[][] res = new double[n][l];

        for (int i = 0; i < n; ++i){
            double sum = 0.0;
            for (int j = 0; j < l; ++j){
                sum += X[i][j];
            }
            mean[i] = sum / l;
        }

        for (int i = 0; i < n; ++i){
            double sum = 0.0;
            for (int j = 0; j < l; ++j) {
                sum += Math.pow(X[i][j] - mean[i], 2);
            }
            std[i] = Math.sqrt(sum / (l-1));
        }

        for (int i = 0; i < n; i++){
            for (int j = 0; j < l; j++){
                res[i][j] = (X[i][j] - mean[i]) / std[i];
            }
        }

        return res;
    }

    public static void main(String args[]){
        Matrix X = new Matrix(new double[][]{{6, 4, 9}, {2, 8, 2}, {3, -4, 0}, {2, 6, 9}});
        KShape kshape = new KShape(2, 100);
        double[][] res = kshape.fit(X.getArray());
        System.out.println(Arrays.deepToString(res));

    }


}
