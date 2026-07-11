import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { ProductListComponent } from './product-list.component';
import { ProductService } from '../../core/services/product.service';
import { Product } from '../../core/models/product.model';
import { vi } from 'vitest';

describe('ProductListComponent', () => {
  let fixture: ComponentFixture<ProductListComponent>;
  let component: ProductListComponent;

  const mockProducts: Product[] = [
    { id: 1, sku: 'SKU-001', name: 'Wireless Mouse', description: 'desc', category: 'Electronics', price: 29.99, stock: 150, active: true },
    { id: 2, sku: 'SKU-002', name: 'USB Hub', description: 'desc', category: 'Accessories', price: 24.50, stock: 80, active: true }
  ];

  const productServiceMock = {
    getActive: vi.fn().mockReturnValue(of(mockProducts)),
    delete: vi.fn().mockReturnValue(of(undefined))
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    productServiceMock.getActive.mockReturnValue(of(mockProducts));

    await TestBed.configureTestingModule({
      imports: [ProductListComponent],
      providers: [
        provideRouter([]),
        provideAnimations(),
        { provide: ProductService, useValue: productServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProductListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load active products on init', () => {
    expect(productServiceMock.getActive).toHaveBeenCalled();
    expect(component.products()).toEqual(mockProducts);
  });

  it('should not be loading after data loads', () => {
    expect(component.loading()).toBe(false);
  });
});
