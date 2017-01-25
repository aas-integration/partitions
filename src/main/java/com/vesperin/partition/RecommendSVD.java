package com.vesperin.partition;

import Jama.Matrix;
import Jama.SingularValueDecomposition;

/**
 * @author Huascar Sanchez
 */
public class RecommendSVD {

  /* This is the main method which calls all the functionality of the
   * exercise */
  public static void main(String[] args) {
    RecommendSVD recommend = new RecommendSVD();
    // Create user-item matrix:
    Matrix userItem = recommend.createUserItemMatrix();
    // Get svd decomposition
    SingularValueDecomposition svd = recommend.computeSVD(userItem);
    // Create matrix (vector) for new user:
    Matrix userNew = recommend.createUserNewMatrix();
    // Get coordinates for new user:
    recommend.predictCase(userNew, userItem, svd, 2);
  }

  /* This creates the user-item matrix using JAMA */
  public Matrix createUserItemMatrix() {
    double [][] values = {
      {5, 5, 0, 5},
      {5, 0, 3, 4},
      {3, 4, 0, 3},
      {0, 0, 5, 3},
      {5, 4, 4, 5},
      {5, 4, 5, 5}
    };
    Matrix userItem = new Matrix(values);
    System.out.println("UserItem: Rank = " + userItem.rank());
    userItem.print(3, 0);

    return userItem;
  }

  /* This creates the 3 matrices that are the decomposition of
   * the original one. That is, the User-Item matrix can be
   * represented by 3 other ones:
   * User-Item matrix = U x S x Vt
   * The validation is so you can see we get the original matrix
   * when we do the matrix multiplication of 3 the decomposition
   * matrices */
  public SingularValueDecomposition computeSVD(Matrix matrix) {
    Jama.SingularValueDecomposition svd = matrix.svd();
//    System.out.println("U      svd.getU().print(6, 3);
//      System.out.println("S      svd.getS().print(6, 3);
//        System.out.println("Vt      svd.getV().transpose().print(6, 3);
//          System.out.println("Validation      svd.getU().times(svd.getS()).times(svd.getV().transpose()).print(6, 3);

    return svd;
  }

  /* This creates the new user's vector (matrix) for Bob (as mentioned
   * in Ilya's example. Notice that this a horizontal vector, not a
   * vertical one, as we would get by writing each element in {} by its own,
   * but it is not really important as long as we take care to multiply
   * objects using the correct dimensions according to algebra rules */
  public Matrix createUserNewMatrix() {
    double [][] array = {
      {5, 5, 0, 0, 0, 5}
    };
    Matrix userNew = new Matrix(array);
    System.out.println("UserNew: Rank = " + userNew.rank());
    System.out.println("(Notice is 1 x n vector, but it's correct and ");
    System.out.println("equivalent to Ilya's vectors.)");
    userNew.print(3, 0);

    return userNew;
  }

  /* This is so we can get a subset of the original decomposition
   * above (U x S x Vt). We still have 3 matrices but each of these are
   * just subsets of the original ones. For this subsetting we
   * use only k = 2 (row/columns) from each matrix
   * We now have Uk x Sk x Vtk where we choose k = 2*/
  public Matrix predictCase(Matrix userNew, Matrix userItem,
                            SingularValueDecomposition svd, int k) {
    int m = userItem.getRowDimension();
    int n = userItem.getColumnDimension();
    Matrix Uk = matrixSubset(svd.getU(), m, k);
    Matrix Sk = matrixSubset(svd.getS(), k, k);
    Matrix Vtk = matrixSubset(svd.getV().transpose(), k, n);
    System.out.println(" Uk (k = 2) matrix:");
    Uk.print(6, 3);
    System.out.println(" Sk (k = 2) matrix:");
    Sk.print(6, 3);
    System.out.println(" Vtk (k = 2) matrix:");
    System.out.println("(Note is 2 x n matrix, not n x 2 as Ilya's");
    System.out.println("so be careful how you multiply it)");
    Vtk.print(6, 3);
    System.out.println("Validation (k = 2):");
    System.out.println("(This is an approximation to the original \n" +
      "- UserItem - rating matrix above)");
    Uk.times(Sk).times(Vtk).print(6, 3);
    Matrix prediction = userNew.times(Uk).times(Sk.inverse());
    System.out.println("k+ k + coordinates for new user:");
    prediction.print(6, 3);

    return prediction;
  }

  /* This is the sub-setting method to get only a specified number of
   * rows/columns from a matrix */
  private Matrix matrixSubset(Matrix orig, int m, int k) {
    Matrix newMatrix = new Matrix(m, k);
    for(int i = 0; i < m; i++) {
      for(int j = 0; j < k; j++) {
        newMatrix.set(i, j, orig.get(i, j));
      }
    }

    return newMatrix;
  }


} // end RecommendSVD
