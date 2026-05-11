# Fix OneActiveListingPerVehicleTest.java
path = r'C:\Car Rentail\src\test\java\com\rentflow\listing\OneActiveListingPerVehicleTest.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix setManufactureYear -> setYear
content = content.replace('setManufactureYear(2020)', 'setYear(2020)')

# Fix assertion messages
content = content.replace(
    '.hasMessageContaining("ACTIVE")',
    '.hasMessageContaining("Vehicle must be ACTIVE")'
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed OneActiveListingPerVehicleTest.java')

# Verify the changes
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
print('Line 71:', lines[70].strip())
print('Line 100:', lines[99].strip())
print('Line 111:', lines[110].strip())
print('Line 122:', lines[121].strip())
