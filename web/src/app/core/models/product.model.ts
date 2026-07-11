export interface Product {
  id: number;
  sku: string;
  name: string;
  description: string;
  category: string;
  price: number;
  stock: number;
  active: boolean;
}

export interface ProductRequest {
  sku: string;
  name: string;
  description: string;
  category: string;
  price: number;
  stock: number;
  active: boolean;
}
