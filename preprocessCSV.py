import pandas as pd
import os

file = os.path.abspath("./src/main/resources/imdb_top_1000.csv")
if not os.path.isfile(file):
    print("requested file not exist.")
    exit(1)
# Load the CSV
df = pd.read_csv(file)

# Check if 'movieID' column already exists
if 'movieID' not in df.columns:
    # Add a new column at the first position with IDs starting from 1
    df.insert(0, 'movieID', range(1, len(df) + 1))
    # Save the updated CSV
    df.to_csv(file, index=False)
    print("movieID column added successfully!")
else:
    print("movieID column already exists.")
