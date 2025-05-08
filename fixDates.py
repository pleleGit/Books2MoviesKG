import pandas as pd
from datetime import datetime

# Load the CSV into a pandas DataFrame
data = pd.read_csv("src/main/resources/books/books.csv")

# Function to convert the publication_date to ISO format
def convert_to_iso(date_str):
    # Parse the date using the original format (mm/dd/yyyy)
    try:
        date_obj = datetime.strptime(date_str, "%m/%d/%Y")
        return date_obj.date().isoformat()  # Convert to ISO format (yyyy-mm-dd) and remove time
    except ValueError:
        return date_str  # In case of invalid date, return it as is

# Apply the function to the publication_date column
data['publication_date'] = data['publication_date'].apply(convert_to_iso)

# Save the modified DataFrame back to a new CSV file
data.to_csv("books_modified.csv", index=False)

